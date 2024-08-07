// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cyclicDependencies.ui;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.cyclicDependencies.actions.CyclicDependenciesHandler;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
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
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.List;
import java.util.*;

public final class CyclicDependenciesPanel extends JPanel implements Disposable, UiDataProvider {
  private static final Set<PsiFile> EMPTY_FILE_SET = new HashSet<>(0);

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

  public CyclicDependenciesPanel(@NotNull Project project, @NotNull CyclicDependenciesBuilder builder) {
    super(new BorderLayout());
    myDependencies = builder.getCyclicDependencies();
    myBuilder = builder;
    myProject = project;
    myUsagesPanel =
    new DependenciesUsagesPanel(myProject, Collections.singletonList(builder.getForwardBuilder()));

    Disposer.register(this, myUsagesPanel);

    mySettings.UI_SHOW_MODULES = false; //exist without modules - and doesn't with

    final Splitter treeSplitter = new Splitter();
    Disposer.register(this, () -> treeSplitter.dispose());
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLeftTree));
    treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myRightTree));

    final Splitter splitter = new Splitter(true);
    Disposer.register(this, () -> splitter.dispose());
    splitter.setFirstComponent(treeSplitter);
    splitter.setSecondComponent(myUsagesPanel);
    add(splitter, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.NORTH);

    myRightTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myRightTree);
    myLeftTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myLeftTree);

    updateLeftTreeModel();
    updateRightTreeModel();

    myLeftTree.getSelectionModel().addTreeSelectionListener(__ -> {
      updateRightTreeModel();
      myUsagesPanel.setToInitialPosition();
    });

    myRightTree.getSelectionModel().addTreeSelectionListener(__ -> SwingUtilities.invokeLater(() -> {
      Set<PsiFile> searchIn = getSelectedScope(myRightTree);
      final PackageNode selectedPackageNode = getSelectedPackage(myRightTree);
      if (selectedPackageNode == null) {
        return;
      }
      final PackageDependenciesNode nextPackageNode = getNextPackageNode(selectedPackageNode);
      Set<PackageNode> packNodes = new HashSet<>();
      getPackageNodesHierarchy(selectedPackageNode, packNodes);
      Set<PsiFile> searchFor = new HashSet<>();
      for (PackageNode packageNode : packNodes) {
        searchFor.addAll(myBuilder.getDependentFilesInPackage((PsiPackage)packageNode.getPsiElement(),
                                                              (PsiPackage)nextPackageNode.getPsiElement()));
      }
      if (searchIn.isEmpty() || searchFor.isEmpty()) {
        myUsagesPanel.setToInitialPosition();
      }
      else {
        String pack1Name = ((PsiPackage)nextPackageNode.getPsiElement()).getQualifiedName();
        String pack2Name = ((PsiPackage)selectedPackageNode.getPsiElement()).getQualifiedName();
        myBuilder.setRootNodeNameInUsageView(JavaBundle.message("cyclic.dependencies.usage.view.root.node.text",
                                                                         pack1Name, pack2Name));
        myUsagesPanel.findUsages(searchIn, searchFor);
      }
    }));

    initTree(myLeftTree);
    initTree(myRightTree);

    mySettings.UI_FILTER_LEGALS = false;
    mySettings.UI_FLATTEN_PACKAGES = false;

    TreeUtil.promiseSelectFirst(myLeftTree);
  }

  private static void getPackageNodesHierarchy(PackageNode node, Set<? super PackageNode> result){
    result.add(node);
    for (int i = 0; i < node.getChildCount(); i++){
      final TreeNode child = node.getChildAt(i);
      if (child instanceof PackageNode packNode && !result.contains(packNode)) {
        getPackageNodesHierarchy(packNode, result);
      }
    }
  }

  private static @Nullable PackageDependenciesNode getNextPackageNode(DefaultMutableTreeNode node) {
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
    if (node.getChildCount() == 0 || node.getChildCount() > 1 || node.getChildCount() == 1 && node.getChildAt(0) instanceof FileNode){
      result.append(result.length() != 0 ? "." : "").append(node.toString().equals(getDefaultPackageAbbreviation()) ? "" : node.toString());//toString()
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
              .append(node.toString().equals(getDefaultPackageAbbreviation()) ? "" : node.toString());
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

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("CyclicDependencies", group, true);
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
    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    TreeUIHelper.getInstance().installTreeSpeedSearch(tree);

    PopupHandler.installPopupMenu(tree, createTreePopupActions(), "CyclicDependenciesPopup");
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
    myLeftTree.setModel(TreeModelBuilder.createTreeModel(myProject, false, psiFiles, __ -> false, mySettings));
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
            .createTreeModel(myProject, false, dependentFilesInPackage, __ -> false, mySettings).getRoot();
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

  private static @Nullable PackageNode getSelectedPackage(final Tree tree) {
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

  @Override
  public void dispose() {
    TreeModelBuilder.clearCaches(myProject);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, "dependency.viewer.tool.window");
  }

  private class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    private final boolean myLeftTree;

    MyTreeCellRenderer(boolean isLeftTree) {
      myLeftTree = isLeftTree;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
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
          if (element instanceof PsiPackage aPackage) {
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
    CloseAction() {
      super(CommonBundle.messagePointer("action.close"), CodeInsightBundle.messagePointer("action.close.dependency.description"),
            AllIcons.Actions.Cancel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Disposer.dispose(myUsagesPanel);
      DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
    }
  }

  private final class ShowFilesAction extends ToggleAction {
    ShowFilesAction() {
      super(CodeInsightBundle.messagePointer("action.show.files"), CodeInsightBundle.messagePointer("action.show.files.description"),
            AllIcons.FileTypes.Java);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_SHOW_FILES;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
      mySettings.UI_SHOW_FILES = flag;
      rebuild();
    }
  }

  private final class HideOutOfCyclePackagesAction extends ToggleAction {

    HideOutOfCyclePackagesAction() {
      super(JavaBundle.message("hide.out.of.cyclic.packages.action.text"),
            JavaBundle.message("hide.out.of.cyclic.packages.action.description"), AllIcons.General.Filter);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return mySettings.UI_FILTER_OUT_OF_CYCLE_PACKAGES;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DependencyUISettings.getInstance().UI_FILTER_OUT_OF_CYCLE_PACKAGES = state;
      mySettings.UI_FILTER_OUT_OF_CYCLE_PACKAGES = state;
      rebuild();
    }
  }

  private final class GroupByScopeTypeAction extends ToggleAction {
    GroupByScopeTypeAction() {
      super(CodeInsightBundle.messagePointer("action.group.by.scope.type"), CodeInsightBundle.messagePointer("action.group.by.scope.type.description"),
            AllIcons.Actions.GroupByTestProduction);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_GROUP_BY_SCOPE_TYPE;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = flag;
      rebuild();
    }
  }

  private class RerunAction extends AnAction {
    RerunAction(JComponent comp) {
      super(CommonBundle.message("action.rerun"), CodeInsightBundle.message("action.rerun.dependency"), AllIcons.Actions.Rerun);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myBuilder.getScope().isValid());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
      SwingUtilities.invokeLater(() -> new CyclicDependenciesHandler(myProject, myBuilder.getScope()).analyze());
    }
  }

  private static class MyTree extends Tree implements UiDataProvider {
    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      PackageDependenciesNode node = getSelectedNode();
      sink.set(CommonDataKeys.NAVIGATABLE, node);
    }

    public @Nullable PackageDependenciesNode getSelectedNode() {
      TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length != 1) return null;
      final Object lastPathComponent = paths[0].getLastPathComponent();
      if (lastPathComponent instanceof PackageDependenciesNode) {
        return (PackageDependenciesNode)lastPathComponent;
      }
      return (PackageDependenciesNode)((DefaultMutableTreeNode)lastPathComponent).getUserObject();
    }
  }

  public static String getDefaultPackageAbbreviation() {
    return JavaBundle.message("dependencies.tree.node.default.package.abbreviation");
  }
}