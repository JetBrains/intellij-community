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
package com.intellij.cyclicDependencies.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.cyclicDependencies.actions.CyclicDependenciesHandler;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependenciesToolWindow;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.ui.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesPanel extends JPanel implements Disposable, DataProvider {
  private static final HashSet<PsiFile> EMPTY_FILE_SET = new HashSet<>(0);

  private final HashMap<PsiPackage, Set<List<PsiPackage>>> myDependencies;
  private final MyTree myLeftTree = new MyTree();
  private final MyTree myRightTree = new MyTree();
  private final DependenciesUsagesPanel myUsagesPanel;

  private final TreeExpansionMonitor myRightTreeExpansionMonitor;
  private final TreeExpansionMonitor myLeftTreeExpansionMonitor;

  private final Project myProject;
  private final CyclicDependenciesBuilder myBuilder;
  private Content myContent;
  private final DependenciesPanel.DependencyPanelSettings mySettings = new DependenciesPanel.DependencyPanelSettings();
  public static final String DEFAULT_PACKAGE_ABBREVIATION = AnalysisScopeBundle.message("dependencies.tree.node.default.package.abbreviation");


  public CyclicDependenciesPanel(Project project, final CyclicDependenciesBuilder builder) {
    super(new BorderLayout());
    myDependencies = builder.getCyclicDependencies();
    myBuilder = builder;
    myProject = project;
    myUsagesPanel =
    new DependenciesUsagesPanel(myProject, Collections.singletonList(builder.getForwardBuilder()));

    Disposer.register(this, myUsagesPanel);

    mySettings.UI_SHOW_MODULES = false; //exist without modules - and doesn't with

    final Splitter treeSplitter = new Splitter();
    Disposer.register(this, new Disposable(){
      public void dispose() {
        treeSplitter.dispose();
      }
    });
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLeftTree));
    treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myRightTree));

    final Splitter splitter = new Splitter(true);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        splitter.dispose();
      }
    });
    splitter.setFirstComponent(treeSplitter);
    splitter.setSecondComponent(myUsagesPanel);
    add(splitter, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.NORTH);

    myRightTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myRightTree, myProject);
    myLeftTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myLeftTree, myProject);

    updateLeftTreeModel();
    updateRightTreeModel();

    myLeftTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateRightTreeModel();
        myUsagesPanel.setToInitialPosition();
      }
    });

    myRightTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        SwingUtilities.invokeLater(() -> {
          Set<PsiFile> searchIn = getSelectedScope(myRightTree);
          final PackageNode selectedPackageNode = getSelectedPackage(myRightTree);
          if (selectedPackageNode == null) {
            return;
          }
          final PackageDependenciesNode nextPackageNode = getNextPackageNode(selectedPackageNode);
          Set<PsiFile> searchFor = new HashSet<>();
          Set<PackageNode> packNodes = new HashSet<>();
          getPackageNodesHierarchy(selectedPackageNode, packNodes);
          for (PackageNode packageNode : packNodes) {
            searchFor.addAll(myBuilder.getDependentFilesInPackage((PsiPackage)packageNode.getPsiElement(),
                                                                  ((PsiPackage)nextPackageNode.getPsiElement())));
          }
          if (searchIn.isEmpty() || searchFor.isEmpty()) {
            myUsagesPanel.setToInitialPosition();
          }
          else {
            myBuilder.setRootNodeNameInUsageView(AnalysisScopeBundle.message("cyclic.dependencies.usage.view.root.node.text", ((PsiPackage)nextPackageNode.getPsiElement()).getQualifiedName(), ((PsiPackage)selectedPackageNode.getPsiElement()).getQualifiedName()));
            myUsagesPanel.findUsages(searchIn, searchFor);
          }
        });
      }
    });

    initTree(myLeftTree);
    initTree(myRightTree);

    mySettings.UI_FILTER_LEGALS = false;
    mySettings.UI_FLATTEN_PACKAGES = false;

    TreeUtil.selectFirstNode(myLeftTree);
  }

  private static void getPackageNodesHierarchy(PackageNode node, Set<PackageNode> result){
    result.add(node);
    for (int i = 0; i < node.getChildCount(); i++){
      final TreeNode child = node.getChildAt(i);
      if (child instanceof PackageNode){
        final PackageNode packNode = (PackageNode)child;
        if (!result.contains(packNode)){
          getPackageNodesHierarchy(packNode, result);
        }
      }
    }
  }

  @Nullable
  private static PackageDependenciesNode getNextPackageNode(DefaultMutableTreeNode node) {
    DefaultMutableTreeNode child = node;
    while (node != null) {
      if (node instanceof CycleNode) {
        final TreeNode packageDependenciesNode = child.getNextSibling() != null
                                                 ? child.getNextSibling()
                                                 : node.getChildAt(0);
        if (packageDependenciesNode instanceof PackageNode){
          return (PackageNode)packageDependenciesNode;
        }
        if (packageDependenciesNode instanceof ModuleNode){
          return (PackageNode)packageDependenciesNode.getChildAt(0);
        }
      }
      child = node;
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  private static PackageDependenciesNode hideEmptyMiddlePackages(PackageDependenciesNode node, StringBuffer result){
    if (node.getChildCount() == 0 || node.getChildCount() > 1 || (node.getChildCount() == 1 && node.getChildAt(0) instanceof FileNode)){
      result.append(result.length() != 0 ? "." : "").append(node.toString().equals(DEFAULT_PACKAGE_ABBREVIATION) ? "" : node.toString());//toString()
    } else {
      if (node.getChildCount() == 1){
        PackageDependenciesNode child = (PackageDependenciesNode)node.getChildAt(0);
        if (!(node instanceof PackageNode)){  //e.g. modules node
          node.removeAllChildren();
          child = hideEmptyMiddlePackages(child, result);
          node.add(child);
        } else {
          if (child instanceof PackageNode){
            node.removeAllChildren();
            result.append(result.length() != 0 ? "." : "")
              .append(node.toString().equals(DEFAULT_PACKAGE_ABBREVIATION) ? "" : node.toString());
            node = hideEmptyMiddlePackages(child, result);
            ((PackageNode)node).setPackageName(result.toString());//toString()
          }
        }
      }
    }
    return node;
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseAction());
    group.add(new RerunAction(this));
    group.add(new ShowFilesAction());
    group.add(new HideOutOfCyclePackagesAction());
    group.add(new GroupByScopeTypeAction());
    group.add(new ContextHelpAction("dependency.viewer.tool.window"));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  private void rebuild() {
    updateLeftTreeModel();
    updateRightTreeModel();
  }

  private void initTree(final MyTree tree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new MyTreeCellRenderer(tree == myLeftTree));
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    new TreeSpeedSearch(tree);

    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(), ActionManager.getInstance());


  }

  private void updateLeftTreeModel() {
    final Set<PsiPackage> psiPackages = myDependencies.keySet();
    final Set<PsiFile> psiFiles = new HashSet<>();
    for (PsiPackage psiPackage : psiPackages) {
      final Set<List<PsiPackage>> cycles = myDependencies.get(psiPackage);
      if (!mySettings.UI_FILTER_OUT_OF_CYCLE_PACKAGES || cycles != null && !cycles.isEmpty()) {
        psiFiles.addAll(getPackageFiles(psiPackage));
      }
    }
    boolean showFiles = mySettings.UI_SHOW_FILES; //do not show files in the left tree
    mySettings.UI_FLATTEN_PACKAGES = true;
    mySettings.UI_SHOW_FILES = false;
    myLeftTreeExpansionMonitor.freeze();
    myLeftTree.setModel(TreeModelBuilder.createTreeModel(myProject, false, psiFiles, new Marker() {
      public boolean isMarked(VirtualFile file) {
        return false;
      }
    }, mySettings));
    myLeftTreeExpansionMonitor.restore();
    expandFirstLevel(myLeftTree);
    mySettings.UI_SHOW_FILES = showFiles;
    mySettings.UI_FLATTEN_PACKAGES = false;
  }

  private static ActionGroup createTreePopupActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    return group;
  }

  private void updateRightTreeModel() {
    PackageDependenciesNode root = new RootNode(myProject);
    final PackageNode packageNode = getSelectedPackage(myLeftTree);
    if (packageNode != null) {
      boolean group = mySettings.UI_GROUP_BY_SCOPE_TYPE;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = false;
      final PsiPackage aPackage = (PsiPackage)packageNode.getPsiElement();
      final Set<List<PsiPackage>> cyclesOfPackages = myDependencies.get(aPackage);
      for (List<PsiPackage> packCycle : cyclesOfPackages) {
        PackageDependenciesNode[] nodes = new PackageDependenciesNode[packCycle.size()];
        for (int i = packCycle.size() - 1; i >= 0; i--) {
          final PsiPackage psiPackage = packCycle.get(i);
          PsiPackage nextPackage = packCycle.get(i == 0 ? packCycle.size() - 1 : i - 1);
          PsiPackage prevPackage = packCycle.get(i == packCycle.size() - 1 ? 0 : i + 1);
          final Set<PsiFile> dependentFilesInPackage = myBuilder.getDependentFilesInPackage(prevPackage, psiPackage, nextPackage);

          final PackageDependenciesNode pack = (PackageDependenciesNode)TreeModelBuilder
            .createTreeModel(myProject, false, dependentFilesInPackage, new Marker() {
              public boolean isMarked(VirtualFile file) {
                return false;
              }
            }, mySettings).getRoot();
          nodes[i] = hideEmptyMiddlePackages((PackageDependenciesNode)pack.getChildAt(0), new StringBuffer());
        }

        PackageDependenciesNode cycleNode = new CycleNode(myProject);
        for (PackageDependenciesNode node : nodes) {
          node.setEquals(true);
          cycleNode.insert(node, 0);
        }
        root.add(cycleNode);
      }
      mySettings.UI_GROUP_BY_SCOPE_TYPE = group;
    }
    myRightTreeExpansionMonitor.freeze();
    myRightTree.setModel(new TreeModel(root, -1, -1));
    myRightTreeExpansionMonitor.restore();
    expandFirstLevel(myRightTree);
  }

  private HashSet<PsiFile> getPackageFiles(final PsiPackage psiPackage) {
    final HashSet<PsiFile> psiFiles = new HashSet<>();
    final PsiClass[] classes = psiPackage.getClasses();
    for (PsiClass aClass : classes) {
      final PsiFile file = aClass.getContainingFile();
      if (myBuilder.getScope().contains(file)) {
        psiFiles.add(file);
      }
    }
    return psiFiles;
  }

  private static void expandFirstLevel(Tree tree) {
    PackageDependenciesNode root = (PackageDependenciesNode)tree.getModel().getRoot();
    int count = root.getChildCount();
    if (count < 10) {
      for (int i = 0; i < count; i++) {
        PackageDependenciesNode child = (PackageDependenciesNode)root.getChildAt(i);
        expandNodeIfNotTooWide(tree, child);
      }
    }
  }

  private static void expandNodeIfNotTooWide(Tree tree, PackageDependenciesNode node) {
    int count = node.getChildCount();
    if (count > 5) return;
    tree.expandPath(new TreePath(node.getPath()));
  }

  @Nullable
  private static PackageNode getSelectedPackage(final Tree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length != 1) return null;
    PackageDependenciesNode node = (PackageDependenciesNode)paths[0].getLastPathComponent();
    if (node.isRoot()) return null;
    if (node instanceof PackageNode) {
      return (PackageNode)node;
    }
    if (node instanceof FileNode) {
      return (PackageNode)node.getParent();
    }
    if (node instanceof ModuleNode){
      return (PackageNode)node.getChildAt(0);
    }
    return null;
  }

  private static Set<PsiFile> getSelectedScope(final Tree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length != 1) return EMPTY_FILE_SET;
    PackageDependenciesNode node = (PackageDependenciesNode)paths[0].getLastPathComponent();
    if (node.isRoot()) return EMPTY_FILE_SET;
    Set<PsiFile> result = new HashSet<>();
    node.fillFiles(result, true);
    return result;
  }

  public void setContent(Content content) {
    myContent = content;
  }

  public void dispose() {
    TreeModelBuilder.clearCaches(myProject);
  }

  @Nullable
  @NonNls
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return "dependency.viewer.tool.window";
    }
    return null;
  }

  private class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    private final boolean myLeftTree;

    public MyTreeCellRenderer(boolean isLeftTree) {
      myLeftTree = isLeftTree;
    }

    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;

      final PackageDependenciesNode node;
      if (value instanceof PackageDependenciesNode){
        node = (PackageDependenciesNode)value;
        if (myLeftTree && !mySettings.UI_FILTER_OUT_OF_CYCLE_PACKAGES) {
          final PsiElement element = node.getPsiElement();
          if (element instanceof PsiPackage) {
            final PsiPackage aPackage = (PsiPackage)element;
            final Set<List<PsiPackage>> packageDependencies = myDependencies.get(aPackage);
            if (packageDependencies != null && !packageDependencies.isEmpty()) {
                attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
            }
          }
        }
      } else {
        node = (PackageDependenciesNode)((DefaultMutableTreeNode)value).getUserObject(); //cycle node children
      }
      append(node.toString(), attributes);
      setIcon(node.getIcon());
    }
  }

  private final class CloseAction extends AnAction implements DumbAware {
    public CloseAction() {
      super(CommonBundle.message("action.close"), AnalysisScopeBundle.message("action.close.dependency.description"),
            AllIcons.Actions.Cancel);
    }

    public void actionPerformed(AnActionEvent e) {
      Disposer.dispose(myUsagesPanel);
      DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
    }
  }

  private final class ShowFilesAction extends ToggleAction {
    ShowFilesAction() {
      super(AnalysisScopeBundle.message("action.show.files"), AnalysisScopeBundle.message("action.show.files.description"),
            AllIcons.FileTypes.Java);
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_SHOW_FILES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
      mySettings.UI_SHOW_FILES = flag;
      rebuild();
    }
  }

  private final class HideOutOfCyclePackagesAction extends ToggleAction {
    @NonNls public static final String SHOW_PACKAGES_FROM_CYCLES_ONLY = "Hide packages without cyclic dependencies";

    HideOutOfCyclePackagesAction() {
      super(SHOW_PACKAGES_FROM_CYCLES_ONLY, SHOW_PACKAGES_FROM_CYCLES_ONLY, AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.UI_FILTER_OUT_OF_CYCLE_PACKAGES;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      DependencyUISettings.getInstance().UI_FILTER_OUT_OF_CYCLE_PACKAGES = state;
      mySettings.UI_FILTER_OUT_OF_CYCLE_PACKAGES = state;
      rebuild();
    }
  }

  private final class GroupByScopeTypeAction extends ToggleAction {
    GroupByScopeTypeAction() {
      super(AnalysisScopeBundle.message("action.group.by.scope.type"), AnalysisScopeBundle.message("action.group.by.scope.type.description"),
            AllIcons.Actions.GroupByTestProduction);
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_GROUP_BY_SCOPE_TYPE;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = flag;
      rebuild();
    }
  }

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super(CommonBundle.message("action.rerun"), AnalysisScopeBundle.message("action.rerun.dependency"), AllIcons.Actions.Rerun);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myBuilder.getScope().isValid());
    }

    public void actionPerformed(AnActionEvent e) {
      DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
      SwingUtilities.invokeLater(() -> new CyclicDependenciesHandler(myProject, myBuilder.getScope()).analyze());
    }
  }

  private static class MyTree extends Tree implements DataProvider {
    public Object getData(String dataId) {
      PackageDependenciesNode node = getSelectedNode();
      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        return node;
      }
      return null;
    }

    @Nullable
    public PackageDependenciesNode getSelectedNode() {
      TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length != 1) return null;
      final Object lastPathComponent = paths[0].getLastPathComponent();
      if (lastPathComponent instanceof PackageDependenciesNode) {
        return (PackageDependenciesNode)lastPathComponent;
      } else {
        return (PackageDependenciesNode)((DefaultMutableTreeNode)lastPathComponent).getUserObject();
      }
    }
  }
}
