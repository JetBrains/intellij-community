// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.TextFieldAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.Enumeration;
import java.util.List;

public class PackageChooserDialog extends PackageChooser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.PackageChooserDialog");

  private Tree myTree;
  private DefaultTreeModel myModel;
  private final Project myProject;
  private final String myTitle;
  private Module myModule;
  private EditorTextField myPathEditor;

  private final Alarm myAlarm = new Alarm(getDisposable());

  public PackageChooserDialog(String title, @NotNull Module module) {
    super(module.getProject(), true);
    setTitle(title);
    myTitle = title;
    myProject = module.getProject();
    myModule = module;
    init();
  }

  public PackageChooserDialog(String title, Project project) {
    super(project, true);
    setTitle(title);
    myTitle = title;
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

    UIUtil.setLineStyleAngled(myTree);
    myTree.setCellRenderer(
      new DefaultTreeCellRenderer() {
        @Override
        public Component getTreeCellRendererComponent(
          JTree tree, Object value,
          boolean sel,
          boolean expanded,
          boolean leaf, int row,
          boolean hasFocus
        ) {
          super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
          setIcon(PlatformIcons.PACKAGE_ICON);

          if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            Object object = node.getUserObject();
            if (object instanceof PsiPackage) {
              String name = ((PsiPackage)object).getName();
              if (name != null && name.length() > 0) {
                setText(name);
              }
              else {
                setText(IdeBundle.message("node.default"));
              }
            }
          }
          return this;
        }
      }
    );

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(JBUI.size(500, 300));

    new TreeSpeedSearch(myTree, path -> {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object object = node.getUserObject();
      if (object instanceof PsiPackage) return ((PsiPackage)object).getName();
      else
        return "";
    });

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        PsiPackage selection = getTreeSelection();
        if (selection != null) {
          String name = selection.getQualifiedName();
          setTitle(myTitle + " - " + ("".equals(name) ? IdeBundle.message("node.default.package") : name));
        }
        else {
          setTitle(myTitle);
        }
        updatePathFromTree();
      }
    });

    panel.add(scrollPane, BorderLayout.CENTER);
    DefaultActionGroup group = createActionGroup(myTree);

    final JPanel northPanel = new JPanel(new BorderLayout());
    panel.add(northPanel, BorderLayout.NORTH);
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(PackageChooserDialog.class.getSimpleName(), group, true);
    northPanel.add(toolBar.getComponent(), BorderLayout.WEST);
    setupPathComponent(northPanel);
    return panel;
  }

  private void setupPathComponent(final JPanel northPanel) {
    northPanel.add(new TextFieldAction() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        toggleShowPathComponent(northPanel, this);
      }
    }, BorderLayout.EAST);
    myPathEditor = new EditorTextField(JavaReferenceEditorUtil.createDocument("", myProject, false), myProject, StdFileTypes.JAVA);
    myPathEditor.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> updateTreeFromPath(), 300);
      }
    });
    myPathEditor.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
    northPanel.add(myPathEditor, BorderLayout.SOUTH);
  }

  private void toggleShowPathComponent(JPanel northPanel, TextFieldAction fieldAction) {
    boolean toShowTextField = !isPathShowing();
    PropertiesComponent.getInstance().setValue(FileChooserDialogImpl.FILE_CHOOSER_SHOW_PATH_PROPERTY, toShowTextField, true);
    myPathEditor.setVisible(toShowTextField);
    fieldAction.update();
    northPanel.revalidate();
    northPanel.repaint();
    updatePathFromTree();
  }

  private static boolean isPathShowing() {
    return PropertiesComponent.getInstance().getBoolean(FileChooserDialogImpl.FILE_CHOOSER_SHOW_PATH_PROPERTY, true);
  }

  private void updatePathFromTree() {
    if (!isPathShowing()) return;
    final PsiPackage selection = getTreeSelection();
    myPathEditor.setText(selection != null ? selection.getQualifiedName() : "");
  }

  private void updateTreeFromPath() {
    selectPackage(myPathEditor.getText().trim());
  }

  private DefaultActionGroup createActionGroup(JComponent component) {
    final DefaultActionGroup group = new DefaultActionGroup();
    final DefaultActionGroup temp = new DefaultActionGroup();
    NewPackageAction newPackageAction = new NewPackageAction();
    newPackageAction.enableInModalConext();
    newPackageAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_NEW_ELEMENT).getShortcutSet(), component);
    temp.add(newPackageAction);
    group.add(temp);
    return group;
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
  public void selectPackage(final String qualifiedName) {
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

  private void createTreeModel() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final FileIndex fileIndex = myModule != null ? ModuleRootManager.getInstance(myModule).getFileIndex() : ProjectRootManager.getInstance(myProject).getFileIndex();
    fileIndex.iterateContent(
      fileOrDir -> {
        if (fileOrDir.isDirectory() && fileIndex.isUnderSourceRootOfType(fileOrDir, JavaModuleSourceRootTypes.SOURCES)) {
          final PsiDirectory psiDirectory = psiManager.findDirectory(fileOrDir);
          LOG.assertTrue(psiDirectory != null);
          PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
          if (aPackage != null){
            addPackage(aPackage);
          }
        }
        return true;
      }
    );

    TreeUtil.sort(myModel, (o1, o2) -> {
      DefaultMutableTreeNode n1 = (DefaultMutableTreeNode) o1;
      DefaultMutableTreeNode n2 = (DefaultMutableTreeNode) o2;
      PsiNamedElement element1 = (PsiNamedElement) n1.getUserObject();
      PsiNamedElement element2 = (PsiNamedElement) n2.getUserObject();
      return element1.getName().compareToIgnoreCase(element2.getName());
    });
  }

  @NotNull
  private DefaultMutableTreeNode addPackage(PsiPackage aPackage) {
    final String qualifiedPackageName = aPackage.getQualifiedName();
    final PsiPackage parentPackage = aPackage.getParentPackage();
    if (parentPackage == null) {
      final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)myModel.getRoot();
      if (qualifiedPackageName.length() == 0) {
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
      final DefaultMutableTreeNode parentNode = addPackage(parentPackage);
      DefaultMutableTreeNode packageNode = findPackageNode(parentNode, qualifiedPackageName);
      if (packageNode != null) {
        return packageNode;
      }
      packageNode = new DefaultMutableTreeNode(aPackage);
      parentNode.add(packageNode);
      return packageNode;
    }
  }

  @Nullable
  private static DefaultMutableTreeNode findPackageNode(DefaultMutableTreeNode rootNode, String qualifiedName) {
    for (int i = 0; i < rootNode.getChildCount(); i++) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)rootNode.getChildAt(i);
      final PsiPackage nodePackage = (PsiPackage)child.getUserObject();
      if (nodePackage != null) {
        if (Comparing.equal(nodePackage.getQualifiedName(), qualifiedName)) return child;
      }
    }
    return null;
  }

  private DefaultMutableTreeNode findNodeForPackage(String qualifiedPackageName) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myModel.getRoot();
    Enumeration enumeration = root.depthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      Object o = enumeration.nextElement();
      if (o instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)o;
        PsiPackage nodePackage = (PsiPackage)node.getUserObject();
        if (nodePackage != null) {
          if (Comparing.equal(nodePackage.getQualifiedName(), qualifiedPackageName)) return node;
        }
      }
    }
    return null;
  }

  private void createNewPackage() {
    final PsiPackage selectedPackage = getTreeSelection();
    if (selectedPackage == null) return;

    final String newPackageName = Messages.showInputDialog(myProject, IdeBundle.message("prompt.enter.a.new.package.name"), IdeBundle.message("title.new.package"), Messages.getQuestionIcon(), "",
                                                           new InputValidator() {
                                                             @Override
                                                             public boolean checkInput(final String inputString) {
                                                               return inputString != null && inputString.length() > 0;
                                                             }

                                                             @Override
                                                             public boolean canClose(final String inputString) {
                                                               return checkInput(inputString);
                                                             }
                                                           });
    if (newPackageName == null) return;

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final Runnable action = () -> {

        try {
          String newQualifiedName = selectedPackage.getQualifiedName();
          if (!Comparing.strEqual(newQualifiedName,"")) newQualifiedName += ".";
          newQualifiedName += newPackageName;
          final PsiDirectory dir = PackageUtil.findOrCreateDirectoryForPackage(myProject, newQualifiedName, null, false);
          if (dir == null) return;
          final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(dir);

          if (newPackage != null) {
            DefaultMutableTreeNode newChild = addPackage(newPackage);

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
    },
                                                  IdeBundle.message("command.create.new.package"),
                                                  null);
  }

  private class NewPackageAction extends AnAction {
    NewPackageAction() {
      super(IdeBundle.message("action.new.package"),
            IdeBundle.message("action.description.create.new.package"), AllIcons.Actions.NewFolder);
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

    public void enableInModalConext() {
      setEnabledInModalContext(true);
    }
  }

}

