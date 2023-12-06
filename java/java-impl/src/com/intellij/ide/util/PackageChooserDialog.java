// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

public class PackageChooserDialog extends PackageChooser {
  private static final Logger LOG = Logger.getInstance(PackageChooserDialog.class);

  private final @NotNull Project myProject;

  private Module myModule;

  private Tree myTree;

  private DefaultTreeModel myModel;

  private EditorTextField myPathEditor;

  private final Alarm myAlarm = new Alarm(getDisposable());

  public PackageChooserDialog(@NlsContexts.DialogTitle @NotNull String title, @NotNull Module module) {
    super(module.getProject(), true);
    setTitle(title);
    myProject = module.getProject();
    myModule = module;
    init();
  }

  public PackageChooserDialog(@NlsContexts.DialogTitle @NotNull String title, @NotNull Project project) {
    super(project, true);
    setTitle(title);
    myProject = project;
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    myModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    createTreeModel();
    myTree = new Tree(myModel);
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(
        @NotNull JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus
      ) {
        setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Package));
        if (value instanceof DefaultMutableTreeNode node) {
          Object object = node.getUserObject();
          if (object instanceof PsiPackage pkg) {
            String name = pkg.getName();
            if (name != null && !name.isEmpty()) {
              append(name);
            }
            else {
              append(IdeCoreBundle.message("node.default"));
            }
          }
        }
      }
    });

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(JBUI.size(500, 300));

    TreeSpeedSearch.installOn(myTree, canExpandInSpeedSearch(), path -> {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object object = node.getUserObject();
      if (object instanceof PsiPackage) return ((PsiPackage)object).getName(); else return "";
    });

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updatePathFromTree();
      }
    });

    panel.add(scrollPane, BorderLayout.CENTER);

    JPanel northPanel = new JPanel(new BorderLayout(8, 0));
    northPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    panel.add(northPanel, BorderLayout.NORTH);
    ActionButton actionButton = createNewPackageActionButton();
    northPanel.add(actionButton, BorderLayout.WEST);
    setupPathComponent(northPanel);
    return panel;
  }

  private ActionButton createNewPackageActionButton() {
    NewPackageAction newPackageAction = new NewPackageAction();
    newPackageAction.enableInModalContext();
    ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_NEW_ELEMENT).getShortcutSet();
    newPackageAction.registerCustomShortcutSet(shortcutSet, myTree);
    return new ActionButton(
      newPackageAction,
      null,
      ActionPlaces.UNKNOWN,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
    );
  }

  protected boolean canExpandInSpeedSearch() {
    return false;
  }

  private void setupPathComponent(JPanel northPanel) {
    myPathEditor = new EditorTextField(JavaReferenceEditorUtil.createDocument("", myProject, false), myProject, JavaFileType.INSTANCE);
    myPathEditor.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> updateTreeFromPath(), 300);
      }
    });
    northPanel.add(myPathEditor, BorderLayout.CENTER);
  }

  private static boolean isPathShowing() {
    return PropertiesComponent.getInstance().getBoolean(FileChooserDialogImpl.FILE_CHOOSER_SHOW_PATH_PROPERTY, true);
  }

  private void updatePathFromTree() {
    if (!isPathShowing()) return;
    PsiPackage selection = getTreeSelection();
    myPathEditor.setText(selection != null ? selection.getQualifiedName() : "");
  }

  private void updateTreeFromPath() {
    selectPackage(myPathEditor.getText().trim());
  }

  @Override
  public String getDimensionServiceKey(){
    return "#com.intellij.ide.util.PackageChooserDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent(){
    return myTree;
  }

  @Override
  public PsiPackage getSelectedPackage(){
    if (getExitCode() == CANCEL_EXIT_CODE) return null;
    return getTreeSelection();
  }

  @Override
  public List<PsiPackage> getSelectedPackages() {
    return TreeUtil.collectSelectedObjectsOfType(myTree, PsiPackage.class);
  }

  @Override
  public void selectPackage(String qualifiedName) {
    /*ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {*/
          DefaultMutableTreeNode node = findNodeForPackage(qualifiedName);
          if (node != null) {
            TreePath path = new TreePath(node.getPath());
            TreeUtil.selectPath(myTree, path);
          }
       /* }
      }, ModalityState.stateForComponent(getRootPane()));*/
  }

  @Nullable
  private PsiPackage getTreeSelection() {
    if (myTree == null) return null;
    TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    return (PsiPackage)node.getUserObject();
  }


  /**
   * A {@link PsiPackage} wrapper that eagerly calculates the package hierarchy.
   */
  static class PackageUpdate {
    private final @NotNull PsiPackage myPkg;

    private final @Nullable PackageUpdate myParentUpdate;

    PackageUpdate(@NotNull PsiPackage pkg) {
      myPkg = pkg;
      PsiPackage parentPkg = pkg.getParentPackage();
      myParentUpdate = parentPkg == null ? null : new PackageUpdate(parentPkg);
    }

    @NotNull PsiPackage getPkg() {
      return myPkg;
    }

    @Nullable PackageUpdate getParentUpdate() {
      return myParentUpdate;
    }
  }

  void createTreeModel() {
    List<PackageUpdate> pkgUpdates = ActionUtil.underModalProgress(
      myProject,
      JavaBundle.message("package.chooser.modal.progress.title"
      ), () -> {
        PsiManager psiManager = PsiManager.getInstance(myProject);
        FileIndex fileIndex = myModule != null
                              ? ModuleRootManager.getInstance(myModule).getFileIndex()
                              : ProjectRootManager.getInstance(myProject).getFileIndex();
        List<PackageUpdate> pkgUpdateList = new ArrayList<>();
        fileIndex.iterateContent(fileOrDir -> {
          if (fileOrDir.isDirectory() && fileIndex.isUnderSourceRootOfType(fileOrDir, JavaModuleSourceRootTypes.SOURCES)) {
            PsiDirectory psiDirectory = psiManager.findDirectory(fileOrDir);
            ProgressManager.checkCanceled();
            LOG.assertTrue(psiDirectory != null);
            PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(psiDirectory);
            if (pkg != null) pkgUpdateList.add(new PackageUpdate(pkg));
          }
          return true;
        });
        return pkgUpdateList;
      });
    for (PackageUpdate pkgUpdate : pkgUpdates) addPackage(pkgUpdate);
    TreeUtil.sort(myModel, (o1, o2) -> {
      PsiNamedElement element1 = (PsiNamedElement)((DefaultMutableTreeNode)o1).getUserObject();
      PsiNamedElement element2 = (PsiNamedElement)((DefaultMutableTreeNode)o2).getUserObject();
      return Objects.requireNonNull(element1.getName()).compareToIgnoreCase(Objects.requireNonNull(element2.getName()));
    });
  }

  @NotNull
  private DefaultMutableTreeNode addPackage(@NotNull PackageChooserDialog.PackageUpdate pkgUpdate) {
    PsiPackage aPackage = pkgUpdate.getPkg();
    String qualifiedPackageName = aPackage.getQualifiedName();
    if (pkgUpdate.getParentUpdate() == null) {
      DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)myModel.getRoot();
      if (qualifiedPackageName.isEmpty()) {
        rootNode.setUserObject(aPackage);
        return rootNode;
      }
      else {
        DefaultMutableTreeNode packageNode = findPackageNode(rootNode, qualifiedPackageName);
        if (packageNode != null) return packageNode;
        packageNode = new DefaultMutableTreeNode(aPackage);
        rootNode.add(packageNode);
        return packageNode;
      }
    }
    else {
      DefaultMutableTreeNode parentNode = addPackage(pkgUpdate.getParentUpdate());
      DefaultMutableTreeNode packageNode = findPackageNode(parentNode, qualifiedPackageName);
      if (packageNode != null) return packageNode;
      packageNode = new DefaultMutableTreeNode(aPackage);
      parentNode.add(packageNode);
      return packageNode;
    }
  }

  @Nullable
  private static DefaultMutableTreeNode findPackageNode(DefaultMutableTreeNode rootNode, String qualifiedName) {
    for (int i = 0; i < rootNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)rootNode.getChildAt(i);
      PsiPackage nodePackage = (PsiPackage)child.getUserObject();
      if (nodePackage != null) {
        if (Objects.equals(nodePackage.getQualifiedName(), qualifiedName)) return child;
      }
    }
    return null;
  }

  private DefaultMutableTreeNode findNodeForPackage(String qualifiedPackageName) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myModel.getRoot();
    Enumeration<TreeNode> enumeration = root.depthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      if (enumeration.nextElement() instanceof DefaultMutableTreeNode node) {
        PsiPackage nodePackage = (PsiPackage)node.getUserObject();
        if (nodePackage != null) {
          if (Objects.equals(nodePackage.getQualifiedName(), qualifiedPackageName)) return node;
        }
      }
    }
    return null;
  }

  private void createNewPackage() {
    PsiPackage selectedPackage = getTreeSelection();
    if (selectedPackage == null) return;
    String newPackageName = Messages.showInputDialog(
      myProject,
      IdeBundle.message("prompt.enter.a.new.package.name"),
      IdeBundle.message("title.new.package"),
      null, "",
      new InputValidator() {
        @Override
        public boolean checkInput(String inputString) {
          return inputString != null && !inputString.isEmpty();
        }

        @Override
        public boolean canClose(String inputString) {
          return checkInput(inputString);
        }
      }
    );
    if (newPackageName == null) return;

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      Runnable action = () -> {
        try {
          String newQualifiedName = selectedPackage.getQualifiedName();
          if (!Comparing.strEqual(newQualifiedName,"")) newQualifiedName += ".";
          newQualifiedName += newPackageName;
          PsiPackage newPackage = getPsiPackage(newQualifiedName);
          if (newPackage != null) {
            DefaultMutableTreeNode newChild = addPackage(new PackageUpdate(newPackage));
            DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
            model.nodeStructureChanged((TreeNode)model.getRoot());
            TreePath path = new TreePath(newChild.getPath());
            myTree.setSelectionPath(path);
            myTree.scrollPathToVisible(path);
            myTree.expandPath(path);
          }
        }
        catch (IncorrectOperationException e) {
          Messages.showMessageDialog(
            getContentPane(),
            StringUtil.getMessage(e),
            CommonBundle.getErrorTitle(),
            Messages.getErrorIcon()
          );
          LOG.debug(e);
        }
      };
      ApplicationManager.getApplication().runReadAction(action);
    }, IdeBundle.message("command.create.new.package"), null);
  }

  @Nullable
  protected PsiPackage getPsiPackage(String newQualifiedName) {
    PsiDirectory dir = PackageUtil.findOrCreateDirectoryForPackage(myProject, newQualifiedName, null, false);
    if (dir == null) return null;
    return JavaDirectoryService.getInstance().getPackage(dir);
  }

  private class NewPackageAction extends AnAction {
    NewPackageAction() {
      super(
        IdeBundle.messagePointer("action.new.package"),
        IdeBundle.messagePointer("action.description.create.new.package"),
        AllIcons.Actions.NewFolder
      );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      createNewPackage();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(getTreeSelection() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    public void enableInModalContext() {
      setEnabledInModalContext(true);
    }
  }
}

