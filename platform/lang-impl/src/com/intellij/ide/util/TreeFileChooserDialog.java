// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePanel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public final class TreeFileChooserDialog extends DialogWrapper implements TreeFileChooser {

  private Tree myTree;
  private PsiFile mySelectedFile = null;
  private final @NotNull Project myProject;
  private StructureTreeModel<? extends ProjectAbstractTreeStructureBase> myModel;
  private TabbedPaneWrapper myTabbedPane;
  private ChooseByNamePanel myGotoByNamePanel;
  private final @Nullable PsiFile myInitialFile;
  private final @Nullable PsiFileFilter myFilter;
  private final @Nullable FileType myFileType;
  private final @NotNull Comparator<? super NodeDescriptor<?>> myComparator;

  private final boolean myDisableStructureProviders;
  private final boolean myShowLibraryContents;
  private boolean mySelectSearchByNameTab = false;

  public TreeFileChooserDialog(@NotNull Project project,
                               @NlsContexts.DialogTitle String title,
                               final @Nullable PsiFile initialFile,
                               @Nullable FileType fileType,
                               @Nullable PsiFileFilter filter,
                               final boolean disableStructureProviders,
                               final boolean showLibraryContents) {
    this(project, title, initialFile, fileType, filter, null, disableStructureProviders, showLibraryContents);
  }

  public TreeFileChooserDialog(@NotNull Project project,
                               @NlsContexts.DialogTitle String title,
                               final @Nullable PsiFile initialFile,
                               @Nullable FileType fileType,
                               @Nullable PsiFileFilter filter,
                               @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                               final boolean disableStructureProviders,
                               final boolean showLibraryContents) {
    super(project, true);
    myInitialFile = initialFile;
    myFilter = filter;
    myFileType = fileType;
    myComparator = comparator == null ? AlphaComparator.getInstance() : comparator;
    myDisableStructureProviders = disableStructureProviders;
    myShowLibraryContents = showLibraryContents;
    setTitle(title);
    myProject = project;
    init();
    if (initialFile != null) {
      // dialog does not exist yet
      SwingUtilities.invokeLater(() -> selectFile(initialFile));
    }

    SwingUtilities.invokeLater(() -> handleSelectionChanged());
  }

  @Override
  protected JComponent createCenterPanel() {
    ProjectAbstractTreeStructureBase treeStructure = new AbstractProjectTreeStructure(myProject) {
      @Override
      protected AbstractTreeNode<?> createRoot(@NotNull Project project, @NotNull ViewSettings settings) {
        return TreeFileChooserSupport.Companion.getInstance(project).createRoot(settings);
      }

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return true;
      }

      @Override
      public Object @NotNull [] getChildElements(final @NotNull Object element) {
        return filterFiles(super.getChildElements(element));
      }

      @Override
      public boolean isShowLibraryContents() {
        return myShowLibraryContents;
      }

      @Override
      public boolean isShowModules() {
        return false;
      }

      @Override
      public List<TreeStructureProvider> getProviders() {
        return myDisableStructureProviders ? null : super.getProviders();
      }

      @Override
      public Object getParentElement(@NotNull Object element) {
        AbstractProjectNode abstractProjectNode = (AbstractProjectNode)getRootElement();
        if (element == abstractProjectNode) {
          return null;
        }

        PsiDirectory psiDirectory = null;
        if (element instanceof PsiFileNode) {
          PsiFile psiFileNode = ((PsiFileNode)element).getValue();
          if (psiFileNode != null) psiDirectory = psiFileNode.getParent();
        } else if (element instanceof PsiDirectoryNode) {
          PsiDirectory psiDirectoryNode = ((PsiDirectoryNode)element).getValue();
          if (psiDirectoryNode != null) {
            if (psiDirectoryNode.getVirtualFile().equals(myProject.getBaseDir())) return abstractProjectNode;
            psiDirectory = psiDirectoryNode.getParent();
          }
        }
        if (psiDirectory == null) return getDefaultParentElement(element);
        return new PsiDirectoryNode(myProject, psiDirectory, abstractProjectNode.getSettings());
      }

      public Object getDefaultParentElement(@NotNull Object element) {
        if (element instanceof AbstractTreeNode) {
          return ((AbstractTreeNode<?>)element).getParent();
        }
        return null;
      }
    };

    myModel = new StructureTreeModel<>(treeStructure, getDisposable());
    myModel.setComparator(myComparator);
    myTree = new Tree(new AsyncTreeModel(myModel, getDisposable()));
    myTree.setRootVisible(false);
    myTree.expandRow(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new NodeRenderer());

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(JBUI.size(500, 300));

    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(final KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          doOKAction();
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        if (path != null && myTree.isPathSelected(path)) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myTree);

    myTree.addTreeSelectionListener(
      new TreeSelectionListener() {
        @Override
        public void valueChanged(final TreeSelectionEvent e) {
          handleSelectionChanged();
        }
      }
    );

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);

    myTabbedPane = new TabbedPaneWrapper(getDisposable());

    final JPanel dummyPanel = new JPanel(new BorderLayout());
    String name = null;
    if (myInitialFile != null) {
      name = myInitialFile.getName();
    }
    myGotoByNamePanel = new ChooseByNamePanel(myProject, new MyGotoFileModel(), name, true, myInitialFile) {
      @Override
      protected void close(final boolean isOk) {
        super.close(isOk);

        if (isOk) {
          doOKAction();
        }
        else {
          doCancelAction();
        }
      }

      @Override
      protected void initUI(final ChooseByNamePopupComponent.Callback callback,
                            final ModalityState modalityState,
                            boolean allowMultipleSelection) {
        super.initUI(callback, modalityState, allowMultipleSelection);
        dummyPanel.add(myGotoByNamePanel.getPanel(), BorderLayout.CENTER);
        //IdeFocusTraversalPolicy.getPreferredFocusedComponent(myGotoByNamePanel.getPanel()).requestFocus();
        if (mySelectSearchByNameTab) {
          myTabbedPane.setSelectedIndex(1);
        }
      }

      @Override
      protected void showTextFieldPanel() {
      }

      @Override
      protected void chosenElementMightChange() {
        handleSelectionChanged();
      }
    };

    myTabbedPane.addTab(IdeBundle.message("tab.chooser.project"), scrollPane);
    myTabbedPane.addTab(IdeBundle.message("tab.chooser.search.by.name"), dummyPanel);

    SwingUtilities.invokeLater(() -> myGotoByNamePanel.invoke(new MyCallback(), ModalityState.stateForComponent(getRootPane()), false));

    myTabbedPane.addChangeListener(
      new ChangeListener() {
        @Override
        public void stateChanged(final ChangeEvent e) {
          handleSelectionChanged();
        }
      }
    );

    return myTabbedPane.getComponent();
  }

  public void selectSearchByNameTab() {
    mySelectSearchByNameTab = true;
  }

  private void handleSelectionChanged(){
    final PsiFile selection = calcSelectedClass();
    setOKActionEnabled(selection != null);
  }

  @Override
  protected void doOKAction() {
    mySelectedFile = calcSelectedClass();
    if (mySelectedFile == null) return;
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    mySelectedFile = null;
    super.doCancelAction();
  }

  @Override
  public PsiFile getSelectedFile(){
    return mySelectedFile;
  }

  @Override
  public void selectFile(@NotNull PsiFile file) {
    // Select element in the tree
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myModel != null) {
        myModel.select(new PsiFileNode(myProject, file, null), myTree, path -> { });
      }
    }, ModalityState.stateForComponent(getWindow()));
  }

  @Override
  public void showDialog() {
    show();
  }

  private PsiFile calcSelectedClass() {
    if (myTabbedPane.getSelectedIndex() == 1) {
      return (PsiFile)myGotoByNamePanel.getChosenElement();
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      VirtualFile vFile = TreeFileChooserSupport.Companion.getInstance(myProject).getVirtualFile(path);
      if (vFile != null && !vFile.isDirectory()) {
        return PsiManager.getInstance(myProject).findFile(vFile);
      }

      return null;
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.TreeFileChooserDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  private final class MyGotoFileModel implements ChooseByNameModel, DumbAware {
    private final int myMaxSize = WindowManagerEx.getInstanceEx().getFrame(myProject).getSize().width;

    @Override
    public Object @NotNull [] getElementsByName(final @NotNull String name, final boolean checkBoxState, final @NotNull String pattern) {
      GlobalSearchScope scope = myShowLibraryContents ? GlobalSearchScope.allScope(myProject) : GlobalSearchScope.projectScope(myProject);
      final PsiFile[] psiFiles = FilenameIndex.getFilesByName(myProject, name, scope);
      return filterFiles(psiFiles);
    }

    @Override
    public String getPromptText() {
      return IdeBundle.message("prompt.filechooser.enter.file.name");
    }

    @Override
    public String getCheckBoxName() {
      return null;
    }


    @Override
    public @NotNull String getNotInMessage() {
      return "";
    }

    @Override
    public @NotNull String getNotFoundMessage() {
      return "";
    }

    @Override
    public boolean loadInitialCheckBoxState() {
      return true;
    }

    @Override
    public void saveInitialCheckBoxState(final boolean state) {
    }

    @Override
    public @NotNull PsiElementListCellRenderer getListCellRenderer() {
      return new GotoFileCellRenderer(myMaxSize);
    }

    @Override
    public String @NotNull [] getNames(final boolean checkBoxState) {
      final String[] fileNames;
      if (myFileType != null) {
        GlobalSearchScope scope = myShowLibraryContents ? GlobalSearchScope.allScope(myProject) : GlobalSearchScope.projectScope(myProject);
        Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(myFileType, scope);
        fileNames = ContainerUtil.map2Array(virtualFiles, String.class, file -> file.getName());
      }
      else {
        fileNames = FilenameIndex.getAllFilenames(myProject);
      }
      Set<String> array = new HashSet<>(fileNames.length);
      Collections.addAll(array, fileNames);
      String[] result = ArrayUtilRt.toStringArray(array);
      Arrays.sort(result);
      return result;
    }

    @Override
    public boolean willOpenEditor() {
      return true;
    }

    @Override
    public String getElementName(final @NotNull Object element) {
      if (!(element instanceof PsiFile)) return null;
      return ((PsiFile)element).getName();
    }

    @Override
    public @Nullable String getFullName(final @NotNull Object element) {
      if (element instanceof PsiFile) {
        final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
        return virtualFile != null ? virtualFile.getPath() : null;
      }

      return getElementName(element);
    }

    @Override
    public String getHelpId() {
      return null;
    }

    @Override
    public String @NotNull [] getSeparators() {
      return new String[] {"/", "\\"};
    }

    @Override
    public boolean useMiddleMatching() {
      return false;
    }
  }

  private final class MyCallback extends ChooseByNamePopupComponent.Callback {
    @Override
    public void elementChosen(final Object element) {
      mySelectedFile = (PsiFile)element;
      close(OK_EXIT_CODE);
    }
  }

  private Object[] filterFiles(final Object[] list) {
    Condition<PsiFile> condition = psiFile -> {
      if (myFilter != null && !myFilter.accept(psiFile)) {
        return false;
      }
      boolean accepted = myFileType == null || psiFile.getFileType() == myFileType;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && !accepted) {
        accepted = FileTypeRegistry.getInstance().isFileOfType(virtualFile, myFileType);
      }
      return accepted;
    };
    final List<Object> result = new ArrayList<>(list.length);
    for (Object o : list) {
      final PsiFile psiFile;
      if (o instanceof PsiFile) {
        psiFile = (PsiFile)o;
      }
      else if (o instanceof PsiFileNode) {
        psiFile = ((PsiFileNode)o).getValue();
      }
      else {
        psiFile = null;
      }
      if (psiFile != null && !condition.value(psiFile)) {
        continue;
      }
      else {
        if (o instanceof ProjectViewNode projectViewNode) {
          if (!projectViewNode.canHaveChildrenMatching(condition)) {
            continue;
          }
        }
      }
      result.add(o);
    }
    return ArrayUtil.toObjectArray(result);
  }
}
