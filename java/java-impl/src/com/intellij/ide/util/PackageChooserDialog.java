/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

public class PackageChooserDialog extends PackageChooser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.PackageChooserDialog");

  private Tree myTree;
  private DefaultTreeModel myModel;
  private final Project myProject;
  private final String myTitle;
  private Module myModule;

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

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());


    myModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    createTreeModel();
    myTree = new Tree(myModel);

    UIUtil.setLineStyleAngled(myTree);
    myTree.setCellRenderer(
      new DefaultTreeCellRenderer() {
        public Component getTreeCellRendererComponent(
          JTree tree, Object value,
          boolean sel,
          boolean expanded,
          boolean leaf, int row,
          boolean hasFocus
        ) {
          super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
          setIcon(expanded ? Icons.PACKAGE_OPEN_ICON : Icons.PACKAGE_ICON);

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

    myTree.setBorder(BorderFactory.createEtchedBorder());
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(500, 300));

    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(TreePath path) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        Object object = node.getUserObject();
        if (object instanceof PsiPackage) return ((PsiPackage)object).getName();
        else
          return "";
      }
    });

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        PsiPackage selection = getTreeSelection();
        if (selection != null) {
          String name = selection.getQualifiedName();
          setTitle(myTitle + " - " + ("".equals(name) ? IdeBundle.message("node.default.package") : name));
        }
        else {
          setTitle(myTitle);
        }
      }
    });

    panel.add(scrollPane, BorderLayout.CENTER);
    DefaultActionGroup group = createActionGroup(myTree);

    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    panel.add(toolBar.getComponent(), BorderLayout.NORTH);
    toolBar.getComponent().setAlignmentX(JComponent.LEFT_ALIGNMENT);

    return panel;
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

  protected void doOKAction(){
    super.doOKAction();
  }

  public String getDimensionServiceKey(){
    return "#com.intellij.ide.util.PackageChooserDialog";
  }

  public JComponent getPreferredFocusedComponent(){
    return myTree;
  }

  public PsiPackage getSelectedPackage(){
    return getTreeSelection();
  }

  public List<PsiPackage> getSelectedPackages() {
    return TreeUtil.collectSelectedObjectsOfType(myTree, PsiPackage.class);
  }

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
      new ContentIterator() {
        public boolean processFile(VirtualFile fileOrDir) {
          if (fileOrDir.isDirectory() && fileIndex.isInSourceContent(fileOrDir)){
            final PsiDirectory psiDirectory = psiManager.findDirectory(fileOrDir);
            LOG.assertTrue(psiDirectory != null);
            PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
            if (aPackage != null){
              addPackage(aPackage);
            }
          }
          return true;
        }
      }
    );

    TreeUtil.sort(myModel, new Comparator() {
      public int compare(Object o1, Object o2) {
        DefaultMutableTreeNode n1 = (DefaultMutableTreeNode) o1;
        DefaultMutableTreeNode n2 = (DefaultMutableTreeNode) o2;
        PsiNamedElement element1 = (PsiNamedElement) n1.getUserObject();
        PsiNamedElement element2 = (PsiNamedElement) n2.getUserObject();
        return element1.getName().compareToIgnoreCase(element2.getName());
      }
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
                                                             public boolean checkInput(final String inputString) {
                                                               return inputString != null && inputString.length() > 0;
                                                             }

                                                             public boolean canClose(final String inputString) {
                                                               return checkInput(inputString);
                                                             }
                                                           });
    if (newPackageName == null) return;

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
              public void run() {

                try {
                  String newQualifiedName = selectedPackage.getQualifiedName();
                  if (!Comparing.strEqual(newQualifiedName,"")) newQualifiedName += ".";
                  newQualifiedName += newPackageName;
                  final PsiDirectory dir = PackageUtil.findOrCreateDirectoryForPackage(myProject, newQualifiedName, null, false);
                  if (dir == null) return;
                  final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(dir);

                  DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
                  final DefaultMutableTreeNode newChild = new DefaultMutableTreeNode();
                  newChild.setUserObject(newPackage);
                  node.add(newChild);

                  final DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
                  model.nodeStructureChanged(node);

                  final TreePath selectionPath = myTree.getSelectionPath();
                  TreePath path;
                  if (selectionPath == null) {
                    path = new TreePath(newChild.getPath());
                  } else {
                    path = selectionPath.pathByAddingChild(newChild);
                  }
                    myTree.setSelectionPath(path);
                    myTree.scrollPathToVisible(path);
                    myTree.expandPath(path);

                }
                catch (IncorrectOperationException e) {
                  Messages.showMessageDialog(
                    getContentPane(),
                    StringUtil.getMessage(e),
                    CommonBundle.getErrorTitle(),
                    Messages.getErrorIcon()
                  );
                  if (LOG.isDebugEnabled()) {
                    LOG.debug(e);
                  }
                }
              }
            };
        ApplicationManager.getApplication().runReadAction(action);
      }
    },
    IdeBundle.message("command.create.new.package"),
    null);
  }

  private class NewPackageAction extends AnAction {
    public NewPackageAction() {
      super(IdeBundle.message("action.new.package"),
            IdeBundle.message("action.description.create.new.package"), IconLoader.getIcon("/actions/newFolder.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      createNewPackage();
    }

    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(getTreeSelection() != null);
    }

    public void enableInModalConext() {
      setEnabledInModalContext(true);
    }
  }

}

