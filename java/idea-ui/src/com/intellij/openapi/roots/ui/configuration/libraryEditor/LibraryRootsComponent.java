// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.PersistentOrderRootType;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.*;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.ModificationOfImportedModelWarningComponent;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class LibraryRootsComponent implements Disposable, LibraryEditorComponent {
  static final UrlComparator ourUrlComparator = new UrlComparator();

  private JPanel myPanel;
  private JPanel myTreePanel;
  private MultiLineLabel myPropertiesLabel;
  private JPanel myPropertiesPanel;
  private JPanel myBottomPanel;
  private LibraryPropertiesEditor myPropertiesEditor;
  private Tree myTree;
  private final ModificationOfImportedModelWarningComponent myModificationOfImportedModelWarningComponent;
  private VirtualFile myLastChosen;

  private final Collection<Runnable> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @Nullable private final Project myProject;

  private final Computable<? extends LibraryEditor> myLibraryEditorComputable;
  private LibraryRootsComponentDescriptor myDescriptor;
  private Module myContextModule;
  private LibraryRootsComponent.AddExcludedRootActionButton myAddExcludedRootActionButton;
  private StructureTreeModel<AbstractTreeStructure> myTreeModel;
  private LibraryRootsComponentDescriptor.RootRemovalHandler myRootRemovalHandler;

  public LibraryRootsComponent(@Nullable Project project, @NotNull LibraryEditor libraryEditor) {
    this(project, new Computable.PredefinedValueComputable<>(libraryEditor));
  }

  public LibraryRootsComponent(@Nullable Project project, @NotNull Computable<? extends LibraryEditor> libraryEditorComputable) {
    myProject = project;
    myLibraryEditorComputable = libraryEditorComputable;
    final LibraryEditor editor = getLibraryEditor();
    final LibraryType type = editor.getType();
    if (type != null) {
      myDescriptor = type.createLibraryRootsComponentDescriptor();
      //noinspection unchecked
      myPropertiesEditor = type.createPropertiesEditor(this);
      if (myPropertiesEditor != null) {
        myPropertiesPanel.add(myPropertiesEditor.createComponent(), BorderLayout.CENTER);
      }
    }
    if (myDescriptor == null) {
      myDescriptor = new DefaultLibraryRootsComponentDescriptor();
    }
    myRootRemovalHandler = myDescriptor.createRootRemovalHandler();
    myModificationOfImportedModelWarningComponent = new ModificationOfImportedModelWarningComponent();
    myBottomPanel.add(BorderLayout.CENTER, myModificationOfImportedModelWarningComponent.getLabel());
    init(new LibraryTreeStructure(this, myDescriptor));
    updatePropertiesLabel();
    onRootsChanged();
  }

  private void onRootsChanged() {
    myAddExcludedRootActionButton.setEnabled(!getNotExcludedRoots().isEmpty());
  }

  @NotNull
  @Override
  public LibraryProperties getProperties() {
    return getLibraryEditor().getProperties();
  }

  @Override
  public boolean isNewLibrary() {
    return getLibraryEditor() instanceof NewLibraryEditor;
  }

  public void updatePropertiesLabel() {
    StringBuilder text = new StringBuilder();
    final LibraryType<?> type = getLibraryEditor().getType();
    final Set<LibraryKind> excluded =
      type != null ? Collections.singleton(type.getKind()) : Collections.emptySet();
    for (String description : LibraryPresentationManager.getInstance().getDescriptions(getLibraryEditor().getFiles(OrderRootType.CLASSES),
                                                                                       excluded)) {
      if (text.length() > 0) {
        text.append("\n");
      }
      text.append(description);
    }
    myPropertiesLabel.setText(text.toString());
  }

  private void init(AbstractTreeStructure treeStructure) {
    myPropertiesLabel.setBorder(JBUI.Borders.empty(0, 10));
    myTreeModel = new StructureTreeModel<>(treeStructure, this);
    AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myTreeModel, this);
    asyncTreeModel.addTreeModelListener(new TreeModelAdapter() {
      @Override
      public void treeNodesInserted(TreeModelEvent event) {
        Object[] childNodes = event.getChildren();
        if (childNodes != null) {
          for (Object childNode : childNodes) {
            LibraryTableTreeContentElement element = TreeUtil.getUserObject(LibraryTableTreeContentElement.class, childNode);
            if (element != null && myTree != null) {
              myTreeModel.expand(element, myTree, path -> { });
            }
          }
        }
      }
    });
    myTree = new Tree(asyncTreeModel);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    new LibraryRootsTreeSpeedSearch(myTree);
    myTree.setCellRenderer(new LibraryTreeRenderer());
    myTreePanel.setLayout(new BorderLayout());

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myTree).disableUpDownActions()
      .setPanelBorder(JBUI.Borders.empty())
      .setRemoveActionName(JavaUiBundle.message("library.remove.action"))
      .disableRemoveAction();
    final List<AttachRootButtonDescriptor> popupItems = new ArrayList<>();
    for (AttachRootButtonDescriptor descriptor : myDescriptor.createAttachButtons()) {
      Icon icon = descriptor.getToolbarIcon();
      if (icon != null) {
        AttachItemAction action = new AttachItemAction(descriptor, descriptor.getButtonText(), icon);
        toolbarDecorator.addExtraAction(AnActionButton.fromAction(action));
      }
      else {
        popupItems.add(descriptor);
      }
    }
    myAddExcludedRootActionButton = new AddExcludedRootActionButton();
    toolbarDecorator.addExtraAction(myAddExcludedRootActionButton);
    toolbarDecorator.addExtraAction(new AnActionButton(JavaUiBundle.messagePointer("action.AnActionButton.text.remove"), IconUtil.getRemoveIcon()) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final List<Object> selectedElements = getSelectedElements();
        if (selectedElements.isEmpty()) {
          return;
        }

        ApplicationManager.getApplication().runWriteAction(() -> {
          for (Object selectedElement : selectedElements) {
            LibraryEditor libraryEditor = getLibraryEditor();
            if (selectedElement instanceof ItemElement) {
              final ItemElement itemElement = (ItemElement)selectedElement;
              libraryEditor.removeRoot(itemElement.getUrl(), itemElement.getRootType());
              myRootRemovalHandler.onRootRemoved(itemElement.getUrl(), itemElement.getRootType(), libraryEditor);
            }
            else if (selectedElement instanceof OrderRootTypeElement) {
              final OrderRootType rootType = ((OrderRootTypeElement)selectedElement).getOrderRootType();
              final String[] urls = libraryEditor.getUrls(rootType);
              for (String url : urls) {
                libraryEditor.removeRoot(url, rootType);
              }
            }
            else if (selectedElement instanceof ExcludedRootElement) {
              libraryEditor.removeExcludedRoot(((ExcludedRootElement)selectedElement).getUrl());
            }
          }
        });
        libraryChanged(true);
      }

      @Override
      public void updateButton(@NotNull AnActionEvent e) {
        super.updateButton(e);
        Presentation presentation = e.getPresentation();
        if (ContainerUtil.and(getSelectedElements(), new FilteringIterator.InstanceOf<>(ExcludedRootElement.class))) {
          presentation.setText(JavaUiBundle.message("action.text.cancel.exclusion"));
        }
        else {
          presentation.setText(getTemplatePresentation().getText());
        }
      }

      @Override
      public ShortcutSet getShortcut() {
        return CommonShortcuts.getDelete();
      }
    });
    toolbarDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        AttachFilesAction attachFilesAction = new AttachFilesAction(myDescriptor.getAttachFilesActionName());
        if (popupItems.isEmpty()) {
          attachFilesAction.perform();
          return;
        }

        List<AnAction> actions = new ArrayList<>();
        actions.add(attachFilesAction);
        for (AttachRootButtonDescriptor descriptor : popupItems) {
          actions.add(new AttachItemAction(descriptor, descriptor.getButtonText(), null));
        }
        final DefaultActionGroup group = new DefaultActionGroup(actions);
        JBPopupFactory.getInstance().createActionGroupPopup(null, group,
                                                            DataManager.getInstance().getDataContext(button.getContextComponent()),
                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
          .show(button.getPreferredPopupPoint());
      }
    });

    myTreePanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  @Nullable
  public Project getProject() {
    return myProject;
  }

  public void setContextModule(Module module) {
    myContextModule = module;
  }

  @Override
  @Nullable
  public VirtualFile getExistingRootDirectory() {
    for (OrderRootType orderRootType : OrderRootType.getAllPersistentTypes()) {
      final VirtualFile[] existingRoots = getLibraryEditor().getFiles(orderRootType);
      if (existingRoots.length > 0) {
        VirtualFile existingRoot = existingRoots[0];
        if (existingRoot.getFileSystem() instanceof JarFileSystem) {
          existingRoot = JarFileSystem.getInstance().getVirtualFileForJar(existingRoot);
        }
        if (existingRoot != null) {
          if (existingRoot.isDirectory()) {
            return existingRoot;
          }
          else {
            return existingRoot.getParent();
          }
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getBaseDirectory() {
    if (myProject != null) {
      //todo[nik] perhaps we shouldn't select project base dir if global library is edited
      return myProject.getBaseDir();
    }
    return null;
  }

  @Override
  public LibraryEditor getLibraryEditor() {
    return myLibraryEditorComputable.compute();
  }

  public boolean hasChanges() {
    if (myPropertiesEditor != null && myPropertiesEditor.isModified()) {
      return true;
    }
    return getLibraryEditor().hasChanges();
  }

  @NotNull
  private List<Object> getSelectedElements() {
    final TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) {
      return Collections.emptyList();
    }

    List<Object> elements = new ArrayList<>();
    for (TreePath selectionPath : selectionPaths) {
      final Object pathElement = getPathElement(selectionPath);
      if (pathElement != null) {
        elements.add(pathElement);
      }
    }
    return elements;
  }

  public void onLibraryRenamed() {
    updateModificationOfImportedModelWarning();
  }

  @Nullable
  private static Object getPathElement(final TreePath selectionPath) {
    if (selectionPath == null) {
      return null;
    }
    final DefaultMutableTreeNode lastPathComponent = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    if (lastPathComponent == null) {
      return null;
    }
    final Object userObject = lastPathComponent.getUserObject();
    if (!(userObject instanceof NodeDescriptor)) {
      return null;
    }
    final Object element = ((NodeDescriptor)userObject).getElement();
    if (!(element instanceof LibraryTableTreeContentElement)) {
      return null;
    }
    return element;
  }

  @Override
  public void renameLibrary(String newName) {
    final LibraryEditor libraryEditor = getLibraryEditor();
    libraryEditor.setName(newName);
    libraryChanged(false);
  }

  @Override
  public void dispose() {
    if (myPropertiesEditor != null) {
      myPropertiesEditor.disposeUIResources();
    }
  }

  public void resetProperties() {
    if (myPropertiesEditor != null) {
      myPropertiesEditor.reset();
    }
  }

  public void applyProperties() {
    if (myPropertiesEditor != null && myPropertiesEditor.isModified()) {
      myPropertiesEditor.apply();
    }
  }

  @Override
  public void updateRootsTree() {
    myTreeModel.invalidate();
  }

  @Nullable
  private VirtualFile getFileToSelect() {
    if (myLastChosen != null) {
      return myLastChosen;
    }

    final VirtualFile directory = getExistingRootDirectory();
    if (directory != null) {
      return directory;
    }
    return getBaseDirectory();
  }

  private class AttachFilesAction extends AttachItemActionBase {
    AttachFilesAction(String title) {
      super(title);
    }

    @Override
    protected List<OrderRoot> selectRoots(@Nullable VirtualFile initialSelection) {
      final String name = getLibraryEditor().getName();
      final FileChooserDescriptor chooserDescriptor = myDescriptor.createAttachFilesChooserDescriptor(name);
      if (myContextModule != null) {
        chooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myContextModule);
      }
      final VirtualFile[] files = FileChooser.chooseFiles(chooserDescriptor, myPanel, myProject, initialSelection);
      if (files.length == 0) return Collections.emptyList();

      return RootDetectionUtil.detectRoots(Arrays.asList(files), myPanel, myProject, myDescriptor);
    }
  }

  public abstract class AttachItemActionBase extends DumbAwareAction {
    protected AttachItemActionBase(String text) {
      super(text);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      perform();
    }

    void perform() {
      VirtualFile toSelect = getFileToSelect();
      List<OrderRoot> roots = selectRoots(toSelect);
      if (roots.isEmpty()) return;

      final List<OrderRoot> attachedRoots = attachFiles(roots);
      final OrderRoot first = ContainerUtil.getFirstItem(attachedRoots);
      if (first != null) {
        myLastChosen = first.getFile();
      }
      fireLibraryChanged();
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
    }

    protected abstract List<OrderRoot> selectRoots(@Nullable VirtualFile initialSelection);
  }

  private class AttachItemAction extends AttachItemActionBase {
    private final AttachRootButtonDescriptor myDescriptor;

    protected AttachItemAction(AttachRootButtonDescriptor descriptor, String title, final Icon icon) {
      super(title);
      getTemplatePresentation().setIcon(icon);
      myDescriptor = descriptor;
    }

    @Override
    protected List<OrderRoot> selectRoots(@Nullable VirtualFile initialSelection) {
      final VirtualFile[] files = myDescriptor.selectFiles(myPanel, initialSelection, myContextModule, getLibraryEditor());
      if (files.length == 0) return Collections.emptyList();

      List<OrderRoot> roots = new ArrayList<>();
      for (VirtualFile file : myDescriptor.scanForActualRoots(files, myPanel)) {
        roots.add(new OrderRoot(file, myDescriptor.getRootType(), myDescriptor.addAsJarDirectories()));
      }
      return roots;
    }
  }

  private List<OrderRoot> attachFiles(List<? extends OrderRoot> roots) {
    final List<OrderRoot> rootsToAttach = filterAlreadyAdded(roots);
    if (!rootsToAttach.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(() -> getLibraryEditor().addRoots(rootsToAttach));
      updatePropertiesLabel();
      onRootsChanged();
      myTreeModel.invalidate();
    }
    return rootsToAttach;
  }

  private List<OrderRoot> filterAlreadyAdded(@NotNull List<? extends OrderRoot> roots) {
    List<OrderRoot> result = new ArrayList<>();
    for (OrderRoot root : roots) {
      final VirtualFile[] libraryFiles = getLibraryEditor().getFiles(root.getType());
      if (!ArrayUtil.contains(root.getFile(), libraryFiles)) {
        result.add(root);
      }
    }
    return result;
  }

  private void libraryChanged(boolean putFocusIntoTree) {
    onRootsChanged();
    updatePropertiesLabel();
    myTreeModel.invalidate();
    if (putFocusIntoTree) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
    }
    fireLibraryChanged();
  }

  private void fireLibraryChanged() {
    for (Runnable listener : myListeners) {
      listener.run();
    }
    updateModificationOfImportedModelWarning();
  }

  private void updateModificationOfImportedModelWarning() {
    LibraryEditor libraryEditor = getLibraryEditor();
    ProjectModelExternalSource externalSource = libraryEditor.getExternalSource();
    if (externalSource != null && hasChanges()) {
      String name = libraryEditor instanceof ExistingLibraryEditor ? ((ExistingLibraryEditor)libraryEditor).getLibrary().getName() : libraryEditor.getName();
      myModificationOfImportedModelWarningComponent.showWarning(name != null ? "Library '" + name + "'" : "Library", externalSource);
    }
    else {
      myModificationOfImportedModelWarningComponent.hideWarning();
    }
  }

  public void addListener(Runnable listener) {
    myListeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    myListeners.remove(listener);
  }

  private Set<VirtualFile> getNotExcludedRoots() {
    Set<VirtualFile> roots = new LinkedHashSet<>();
    String[] excludedRootUrls = getLibraryEditor().getExcludedRootUrls();
    Set<VirtualFile> excludedRoots = new HashSet<>();
    for (String url : excludedRootUrls) {
      ContainerUtil.addIfNotNull(excludedRoots, VirtualFileManager.getInstance().findFileByUrl(url));
    }
    for (PersistentOrderRootType type : OrderRootType.getAllPersistentTypes()) {
      VirtualFile[] files = getLibraryEditor().getFiles(type);
      for (VirtualFile file : files) {
        if (!VfsUtilCore.isUnder(file, excludedRoots)) {
          roots.add(VfsUtil.getLocalFile(file));
        }
      }
    }
    return roots;
  }

  private class AddExcludedRootActionButton extends AnActionButton {
    AddExcludedRootActionButton() {
      super(CommonBundle.messagePointer("button.exclude"), Presentation.NULL_STRING, AllIcons.Modules.AddExcludedRoot);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
      descriptor.setTitle(JavaUiBundle.message("chooser.title.exclude.from.library"));
      descriptor.setDescription(JavaUiBundle.message(
        "chooser.description.select.directories.which.should.be.excluded.from.the.library.content"));
      Set<VirtualFile> roots = getNotExcludedRoots();
      descriptor.setRoots(roots.toArray(VirtualFile.EMPTY_ARRAY));
      if (roots.size() < 2) {
        descriptor.withTreeRootVisible(true);
      }
      VirtualFile toSelect = null;
      for (Object o : getSelectedElements()) {
        Object itemElement = o instanceof ExcludedRootElement ? ((ExcludedRootElement)o).getParentDescriptor() : o;
        if (itemElement instanceof ItemElement) {
          toSelect = VirtualFileManager.getInstance().findFileByUrl(((ItemElement)itemElement).getUrl());
          break;
        }
      }
      final VirtualFile[] files = FileChooser.chooseFiles(descriptor, myPanel, myProject, toSelect);
      if (files.length > 0) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          for (VirtualFile file : files) {
            getLibraryEditor().addExcludedRoot(file.getUrl());
          }
        });
        myLastChosen = files[0];
        libraryChanged(true);
      }
    }
  }
}
