// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.impl.FlattenModulesToggleAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.packageDependencies.*;
import com.intellij.packageDependencies.actions.AnalyzeDependenciesHandler;
import com.intellij.packageDependencies.actions.BackwardDependenciesHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public final class DependenciesPanel extends JPanel implements Disposable, UiDataProvider {
  private final Map<PsiFile, Set<PsiFile>> myDependencies;
  private Map<VirtualFile, Map<DependencyRule, Set<PsiFile>>> myIllegalDependencies;
  private final MyTree myLeftTree = new MyTree();
  private final MyTree myRightTree = new MyTree();
  private final DependenciesUsagesPanel myUsagesPanel;

  private static final Set<PsiFile> EMPTY_FILE_SET = new HashSet<>(0);
  private final TreeExpansionMonitor myRightTreeExpansionMonitor;
  private final TreeExpansionMonitor myLeftTreeExpansionMonitor;

  private final Marker myRightTreeMarker;
  private final Marker myLeftTreeMarker;
  private Set<VirtualFile> myIllegalsInRightTree = new HashSet<>();

  private final Project myProject;
  private final List<DependenciesBuilder> myBuilders;
  private final Set<PsiFile> myExcluded;
  private Content myContent;
  private final DependencyPanelSettings mySettings = new DependencyPanelSettings();
  private static final Logger LOG = Logger.getInstance(DependenciesPanel.class);

  private final boolean myForward;
  private final AnalysisScope myScopeOfInterest;
  private final int myTransitiveBorder;

  public DependenciesPanel(Project project, final DependenciesBuilder builder){
    this(project, Collections.singletonList(builder), new HashSet<>());
  }

  public DependenciesPanel(Project project, final List<DependenciesBuilder> builders, final Set<PsiFile> excluded) {
    super(new BorderLayout());
    myBuilders = builders;
    myExcluded = excluded;
    final DependenciesBuilder main = myBuilders.get(0);
    myForward = !main.isBackward();
    myScopeOfInterest = main instanceof BackwardDependenciesBuilder ? ((BackwardDependenciesBuilder)main).getScopeOfInterest() : null;
    myTransitiveBorder = main instanceof ForwardDependenciesBuilder ? ((ForwardDependenciesBuilder)main).getTransitiveBorder() : 0;
    myDependencies = new HashMap<>();
    myIllegalDependencies = new HashMap<>();
    for (DependenciesBuilder builder : builders) {
      myDependencies.putAll(builder.getDependencies());
      putAllDependencies(builder);
    }
    exclude(excluded);
    myProject = project;
    myUsagesPanel = new DependenciesUsagesPanel(myProject, myBuilders);
    Disposer.register(this, myUsagesPanel);

    final Splitter treeSplitter = new Splitter();
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        treeSplitter.dispose();
      }
    });
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLeftTree));
    treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myRightTree));

    final Splitter splitter = new Splitter(true);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        splitter.dispose();
      }
    });
    splitter.setFirstComponent(treeSplitter);
    splitter.setSecondComponent(myUsagesPanel);
    add(splitter, BorderLayout.CENTER);

    ActionToolbar toolbar = createToolbar();
    toolbar.setTargetComponent(treeSplitter);
    add(toolbar.getComponent(), BorderLayout.NORTH);

    myRightTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myRightTree);
    myLeftTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myLeftTree);

    myRightTreeMarker = new Marker() {
      @Override
      public boolean isMarked(@NotNull VirtualFile file) {
        return myIllegalsInRightTree.contains(file);
      }
    };

    myLeftTreeMarker = new Marker() {
      @Override
      public boolean isMarked(@NotNull VirtualFile file) {
        return myIllegalDependencies.containsKey(file);
      }
    };

    updateLeftTreeModel();
    updateRightTreeModel();

    myLeftTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateRightTreeModel();
        final StringBuffer denyRules = new StringBuffer();
        final StringBuffer allowRules = new StringBuffer();
        final TreePath[] paths = myLeftTree.getSelectionPaths();
        if (paths == null) {
          return;
        }
        for (TreePath path : paths) {
          PackageDependenciesNode selectedNode = (PackageDependenciesNode)path.getLastPathComponent();
          traverseToLeaves(selectedNode, denyRules, allowRules);
        }
        if (denyRules.length() + allowRules.length() > 0) {
          StatusBar.Info.set(CodeInsightBundle.message("status.bar.rule.violation.message",
                                                        ((denyRules.length() == 0 || allowRules.length() == 0) ? 1 : 2),
                                                        (denyRules.length() > 0 ? denyRules.toString() + (allowRules.length() > 0 ? "; " : "") : " ") +
                                                        (allowRules.length() > 0 ? allowRules.toString() : " ")), myProject);
        }
        else {
          StatusBar.Info.set(CodeInsightBundle.message("status.bar.no.rule.violation.message"), myProject);
        }
      }
    });

    myRightTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        SwingUtilities.invokeLater(() -> {
          final Set<PsiFile> searchIn = getSelectedScope(myLeftTree);
          final Set<PsiFile> searchFor = getSelectedScope(myRightTree);
          if (searchIn.isEmpty() || searchFor.isEmpty()) {
            myUsagesPanel.setToInitialPosition();
            processDependencies(searchIn, searchFor, path -> {
              searchFor.add(path.get(1));
              return true;
            });
          }
          else {
            myUsagesPanel.findUsages(searchIn, searchFor);
          }
        });
      }
    });

    initTree(myLeftTree, false);
    initTree(myRightTree, true);

    setEmptyText(mySettings.UI_FILTER_LEGALS);

    if (builders.size() == 1) {
      AnalysisScope scope = builders.get(0).getScope();
      if (scope.getScopeType() == AnalysisScope.FILE) {
        Set<PsiFile> oneFileSet = myDependencies.keySet();
        if (oneFileSet.size() == 1) {
          selectElementInLeftTree(oneFileSet.iterator().next());
          return;
        }
      }
    }
    TreeUtil.promiseSelectFirst(myLeftTree);
  }

  private void putAllDependencies(DependenciesBuilder builder) {
    final Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> dependencies = builder.getIllegalDependencies();
    for (Map.Entry<PsiFile, Map<DependencyRule, Set<PsiFile>>> entry : dependencies.entrySet()) {
      myIllegalDependencies.put(entry.getKey().getVirtualFile(), entry.getValue());
    }
  }

  private void processDependencies(final Set<? extends PsiFile> searchIn, final Set<? extends PsiFile> searchFor, Processor<? super List<PsiFile>> processor) {
    if (myTransitiveBorder == 0) return;
    Set<PsiFile> initialSearchFor = new HashSet<>(searchFor);
    for (DependenciesBuilder builder : myBuilders) {
      for (PsiFile from : searchIn) {
        for (PsiFile to : initialSearchFor) {
          final List<List<PsiFile>> paths = builder.findPaths(from, to);
          paths.sort(Comparator.comparingInt(List::size));
          for (List<PsiFile> path : paths) {
            if (!path.isEmpty()){
              path.add(0, from);
              path.add(to);
              if (!processor.process(path)) return;
            }
          }
        }
      }
    }
  }

  private void exclude(final Set<? extends PsiFile> excluded) {
    for (PsiFile psiFile : excluded) {
      myDependencies.remove(psiFile);
      myIllegalDependencies.remove(psiFile);
    }
  }

  private void traverseToLeaves(final PackageDependenciesNode treeNode, final StringBuffer denyRules, final StringBuffer allowRules) {
    final Enumeration enumeration = treeNode.breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      PsiElement childPsiElement = ((PackageDependenciesNode)enumeration.nextElement()).getPsiElement();
      if (myIllegalDependencies.containsKey(childPsiElement)) {
        final Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(childPsiElement);
        for (final DependencyRule rule : illegalDeps.keySet()) {
          if (rule.isDenyRule()) {
            if (denyRules.indexOf(rule.getDisplayText()) == -1) {
              denyRules.append(rule.getDisplayText());
              denyRules.append("\n");
            }
          }
          else {
            if (allowRules.indexOf(rule.getDisplayText()) == -1) {
              allowRules.append(rule.getDisplayText());
              allowRules.append("\n");
            }
          }
        }
      }
    }
  }

  private @NotNull ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseAction());
    group.add(new RerunAction(this));
    group.add(new FlattenPackagesAction());
    group.add(new ShowFilesAction());
    if (ModuleManager.getInstance(myProject).getModules().length > 1) {
      group.add(new ShowModulesAction());
      group.add(createFlattenModulesAction());
      if (ModuleManager.getInstance(myProject).hasModuleGroups()) {
        group.add(new ShowModuleGroupsAction());
      }
    }
    group.add(new GroupByScopeTypeAction());
    //group.add(new GroupByFilesAction());
    group.add(new FilterLegalsAction());
    group.add(new MarkAsIllegalAction());
    group.add(new ChooseScopeTypeAction());
    group.add(new EditDependencyRulesAction());
    group.add(CommonActionsManager.getInstance().createExportToTextFileAction(new DependenciesExporterToTextFile()));

    return ActionManager.getInstance().createActionToolbar("PackageDependencies", group, true);
  }

  private @NotNull FlattenModulesToggleAction createFlattenModulesAction() {
    return new FlattenModulesToggleAction(myProject, () -> mySettings.UI_SHOW_MODULES, () -> !mySettings.UI_SHOW_MODULE_GROUPS, (value) -> {
      DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS = !value;
      mySettings.UI_SHOW_MODULE_GROUPS = !value;
      rebuild();
    });
  }

  private void rebuild() {
    myIllegalDependencies = new HashMap<>();
    for (DependenciesBuilder builder : myBuilders) {
      putAllDependencies(builder);
    }
    updateLeftTreeModel();
    updateRightTreeModel();
  }

  private void initTree(final MyTree tree, boolean isRightTree) {
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    TreeUIHelper.getInstance().installTreeSpeedSearch(tree);

    PopupHandler.installPopupMenu(tree, createTreePopupActions(isRightTree), "DependenciesPopup");
  }

  private void updateRightTreeModel() {
    Set<PsiFile> deps = new HashSet<>();
    Set<PsiFile> scope = getSelectedScope(myLeftTree);
    myIllegalsInRightTree = new HashSet<>();
    for (PsiFile psiFile : scope) {
      Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(psiFile.getVirtualFile());
      if (illegalDeps != null) {
        for (final DependencyRule rule : illegalDeps.keySet()) {
          final Set<PsiFile> files = illegalDeps.get(rule);
          for (PsiFile file : files) {
            myIllegalsInRightTree.add(file.getVirtualFile());
          }
        }
      }
      final Set<PsiFile> psiFiles = myDependencies.get(psiFile);
      if (psiFiles != null) {
        for (PsiFile file : psiFiles) {
          if (file != null && file.isValid()) {
            deps.add(file);
          }
        }
      }
    }
    deps.removeAll(scope);
    replaceModel(myRightTree, buildTreeModel(deps, myRightTreeMarker), myRightTreeExpansionMonitor);
    expandFirstLevel(myRightTree);
  }

  private static void replaceModel(@NotNull MyTree tree, @NotNull AsyncTreeModel model, @NotNull TreeExpansionMonitor<?> monitor) {
    monitor.freeze();
    var oldModel = tree.getModel();
    if (oldModel instanceof Disposable disposable) {
      Disposer.dispose(disposable);
    }
    tree.setModel(model);
    monitor.restoreAsync();
  }

  private ActionGroup createTreePopupActions(boolean isRightTree) {
    DefaultActionGroup group = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(actionManager.getAction(IdeActions.GROUP_VERSION_CONTROLS));

    if (isRightTree) {
      group.add(actionManager.getAction(IdeActions.GROUP_ANALYZE));
      group.add(new AddToScopeAction());
      group.add(new SelectInLeftTreeAction());
      group.add(new ShowDetailedInformationAction());
    } else {
      group.add(new RemoveFromScopeAction());
    }

    return group;
  }

  private AsyncTreeModel buildTreeModel(Set<? extends PsiFile> deps, Marker marker) {
    return new AsyncTreeModel(
      new BackgroundTreeModel(
        () -> Objects.requireNonNull(PatternDialectProvider.getInstance(mySettings.SCOPE_TYPE))
          .createTreeModel(myProject, deps, marker, mySettings),
        false
      ), this
    );
  }

  private void updateLeftTreeModel() {
    Set<PsiFile> psiFiles = myDependencies.keySet();
    replaceModel(myLeftTree, buildTreeModel(psiFiles, myLeftTreeMarker), myLeftTreeExpansionMonitor);
    expandFirstLevel(myLeftTree);
  }

  private static void expandFirstLevel(Tree tree) {
    var model = tree.getModel();
    if (model == null) {
      return;
    }
    // Can't figure child count before a node is expanded, so we have to do this in two passes:
    // 1. first, expand everything that potentially needs expanding;
    // 2. second, collapse back everything that doesn't need to be expanded.
    TreeUtil.promiseExpand(tree, path -> {
      var level = path.getPathCount();
      if (level == 1) {
        return TreeVisitor.Action.CONTINUE; // The root isn't visible and always expanded.
      }
      else {
        var siblingCount = model.getChildCount(path.getParentPath().getLastPathComponent());
        if (level == 2) {
          return siblingCount < 10 ? TreeVisitor.Action.CONTINUE : TreeVisitor.Action.SKIP_SIBLINGS;
        }
        else {
          return siblingCount == 1 ? TreeVisitor.Action.CONTINUE : TreeVisitor.Action.SKIP_SIBLINGS;
        }
      }
    }).onSuccess(ignored -> {
      TreeUtil.promiseVisit(tree, path -> {
        var level = path.getPathCount();
        Object value = path.getLastPathComponent();
        var childCount = model.getChildCount(value);
        if (level == 1) { // Don't even bother visiting the tree if the root has too many children.
          return childCount < 10 ? TreeVisitor.Action.CONTINUE : TreeVisitor.Action.SKIP_CHILDREN;
        }
        else if (!tree.isExpanded(path)) { // Ignore everything that wasn't expanded above.
          return TreeVisitor.Action.SKIP_CHILDREN;
        }
        else {
          if (childCount == 1) { // Check auto expanded nodes, no matter how deep they are.
            return TreeVisitor.Action.CONTINUE;
          }
          else { // Reached the deepest auto expanded node, check its child count.
            if (childCount > 5) {
              tree.collapsePath(path);
            }
            return TreeVisitor.Action.SKIP_CHILDREN; // Same as SKIP_SIBLINGS, really, as there's only one node on this level.
          }
        }
      });
    });
  }

  private Set<PsiFile> getSelectedScope(final Tree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    return getSelectedScope(paths != null ? ContainerUtil.map2Array(paths, p -> p.getLastPathComponent()) : null);
  }

  private Set<PsiFile> getSelectedScope(Object[] nodes) {
    if (nodes == null ) return EMPTY_FILE_SET;
    Set<PsiFile> result = new HashSet<>();
    for (Object n : nodes) {
      ((PackageDependenciesNode)n).fillFiles(result, !mySettings.UI_FLATTEN_PACKAGES);
    }
    return result;
  }

  public void setContent(Content content) {
    myContent = content;
  }

  public JTree getLeftTree() {
    return myLeftTree;
  }

  public JTree getRightTree() {
    return myRightTree;
  }

  @Override
  public void dispose() {
    FileTreeModelBuilder.clearCaches(myProject);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, "dependency.viewer.tool.window");
    DataSink.uiDataSnapshot(sink, myRightTree);
  }

  private static final class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(
      @NotNull JTree tree,
      Object value,
      boolean selected,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus
  ){
      PackageDependenciesNode node = (PackageDependenciesNode)value;
      if (node.isValid()) {
        setIcon(node.getIcon());
      } else {
        append(UsageViewBundle.message("node.invalid") + " ", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      append(node.toString(), node.hasMarked() && !selected ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(node.getPresentableFilesCount(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
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

  private final class FlattenPackagesAction extends ToggleAction {
    FlattenPackagesAction() {
      super(CodeInsightBundle.messagePointer("action.flatten.packages"), CodeInsightBundle.messagePointer("action.flatten.packages"),
            PlatformIcons.FLATTEN_PACKAGES_ICON);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_FLATTEN_PACKAGES;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES = flag;
      mySettings.UI_FLATTEN_PACKAGES = flag;
      rebuild();
    }
  }

  private final class ShowFilesAction extends ToggleAction {
    ShowFilesAction() {
      super(CodeInsightBundle.messagePointer("action.show.files"), CodeInsightBundle.messagePointer("action.show.files.description"),
            AllIcons.FileTypes.Unknown);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_SHOW_FILES;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
      mySettings.UI_SHOW_FILES = flag;
      if (!flag && myLeftTree.getSelectionPath() != null && myLeftTree.getSelectionPath().getLastPathComponent() instanceof FileNode){
        TreeUtil.selectPath(myLeftTree, myLeftTree.getSelectionPath().getParentPath());
      }
      rebuild();
    }
  }

  private final class ShowModulesAction extends ToggleAction {
    ShowModulesAction() {
      super(CodeInsightBundle.messagePointer("action.show.modules"), CodeInsightBundle.messagePointer("action.show.modules.description"),
            AllIcons.Actions.GroupByModule);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_SHOW_MODULES;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_MODULES = flag;
      mySettings.UI_SHOW_MODULES = flag;
      rebuild();
    }
  }

  private final class ShowModuleGroupsAction extends ToggleAction {
    ShowModuleGroupsAction() {
      super(CodeInsightBundle.message("analyze.dependencies.show.module.groups.action.text"),
            CodeInsightBundle.message("analyze.dependencies.show.module.groups.action.text"), AllIcons.Actions.GroupByModuleGroup);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_SHOW_MODULE_GROUPS;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS = flag;
      mySettings.UI_SHOW_MODULE_GROUPS = flag;
      rebuild();
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(ModuleManager.getInstance(myProject).hasModuleGroups());
      e.getPresentation().setEnabled(mySettings.UI_SHOW_MODULES);
    }
  }

  private final class GroupByScopeTypeAction extends ToggleAction {
    GroupByScopeTypeAction() {
      super(CodeInsightBundle.messagePointer("action.group.by.scope.type"),
            CodeInsightBundle.messagePointer("action.group.by.scope.type.description"), AllIcons.Actions.GroupByTestProduction);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_GROUP_BY_SCOPE_TYPE;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = flag;
      rebuild();
    }
  }


  private final class FilterLegalsAction extends ToggleAction {
    FilterLegalsAction() {
      super(CodeInsightBundle.messagePointer("action.show.illegals.only"),
            CodeInsightBundle.messagePointer("action.show.illegals.only.description"), AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return mySettings.UI_FILTER_LEGALS;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
      mySettings.UI_FILTER_LEGALS = flag;
      setEmptyText(flag);
      rebuild();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private void setEmptyText(boolean flag) {
    final String emptyText = flag ? LangBundle.message("status.text.no.illegal.dependencies.found") : LangBundle.message("status.text.nothing.to.show");
    myLeftTree.getEmptyText().setText(emptyText);
    myRightTree.getEmptyText().setText(emptyText);
  }

  private final class EditDependencyRulesAction extends AnAction {
    EditDependencyRulesAction() {
      super(CodeInsightBundle.messagePointer("action.edit.rules"), CodeInsightBundle.messagePointer("action.edit.rules.description"),
            AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      boolean applied = ShowSettingsUtil.getInstance().editConfigurable(DependenciesPanel.this, new DependencyConfigurable(myProject));
      if (applied) {
        rebuild();
      }
    }
  }

  private final class DependenciesExporterToTextFile implements ExporterToTextFile {
    @Override
    public @NotNull String getReportText() {
      final Element rootElement = new Element("root");
      rootElement.setAttribute("isBackward", String.valueOf(!myForward));
      final List<PsiFile> files = new ArrayList<>(myDependencies.keySet());
      files.sort((f1, f2) -> {
        final VirtualFile virtualFile1 = f1.getVirtualFile();
        final VirtualFile virtualFile2 = f2.getVirtualFile();
        if (virtualFile1 != null && virtualFile2 != null) {
          return virtualFile1.getPath().compareToIgnoreCase(virtualFile2.getPath());
        }
        return 0;
      });
      for (PsiFile file : files) {
        final Element fileElement = new Element("file");
        fileElement.setAttribute("path", file.getVirtualFile().getPath());
        for (PsiFile dep : myDependencies.get(file)) {
          Element depElement = new Element("dependency");
          depElement.setAttribute("path", dep.getVirtualFile().getPath());
          fileElement.addContent(depElement);
        }
        rootElement.addContent(fileElement);
      }

      try {
        return JbXmlOutputter.collapseMacrosAndWrite(rootElement, myProject);
      }
      catch (IOException e) {
        LOG.error(e);
        return "";
      }
    }

    @Override
    public @NotNull String getDefaultFilePath() {
      return "";
    }

    @Override
    public boolean canExport() {
      return true;
    }
  }


  private final class RerunAction extends AnAction {
    RerunAction(JComponent comp) {
      super(CommonBundle.message("action.rerun"), CodeInsightBundle.message("action.rerun.dependency"), AllIcons.Actions.Rerun);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = true;
      for (DependenciesBuilder builder : myBuilders) {
        enabled &= builder.getScope().isValid();
      }
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
      SwingUtilities.invokeLater(() -> {
        final List<AnalysisScope> scopes = new ArrayList<>();
        for (DependenciesBuilder builder : myBuilders) {
          final AnalysisScope scope = builder.getScope();
          scope.invalidate();
          scopes.add(scope);
        }
        if (!myForward) {
          new BackwardDependenciesHandler(myProject, scopes, myScopeOfInterest, myExcluded).analyze();
        }
        else {
          new AnalyzeDependenciesHandler(myProject, scopes, myTransitiveBorder, myExcluded).analyze();
        }
      });
    }
  }

  private static final class MyTree extends Tree implements UiDataProvider {
    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      PackageDependenciesNode node = getSelectedNode();
      sink.set(CommonDataKeys.NAVIGATABLE, node);
      TreePath[] paths = getSelectionPaths();
      TreePath path = getSelectionPath();
      sink.set(PlatformCoreDataKeys.SELECTED_ITEMS,
               paths != null ? ContainerUtil.map2Array(paths, p -> p.getLastPathComponent()) : null);
      sink.set(PlatformCoreDataKeys.SELECTED_ITEM,
               path != null ? path.getLastPathComponent() : null);
      if (node == null) return;

      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
        PsiElement element = node.getPsiElement();
        return element != null && element.isValid() ? element : null;
      });
    }

    public @Nullable PackageDependenciesNode getSelectedNode() {
      TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length != 1) return null;
      return (PackageDependenciesNode)paths[0].getLastPathComponent();
    }
  }

  private final class ShowDetailedInformationAction extends AnAction {
    private ShowDetailedInformationAction() {
      super(ActionsBundle.messagePointer("action.ShowDetailedInformationAction.text"));
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      final @NonNls String delim = "&nbsp;-&gt;&nbsp;";
      final StringBuffer buf = new StringBuffer();
      processDependencies(getSelectedScope(myLeftTree), getSelectedScope(myRightTree), path -> {
        if (buf.length() > 0) buf.append("<br>");
        buf.append(StringUtil.join(path, psiFile -> psiFile.getName(), delim));
        return true;
      });
      final JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, XmlStringUtil.wrapInHtml(buf));
      pane.setForeground(JBColor.foreground());
      pane.setBackground(HintUtil.getInformationColor());
      pane.setOpaque(true);
      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
      final Dimension dimension = pane.getPreferredSize();
      scrollPane.setMinimumSize(new Dimension(dimension.width, dimension.height + 20));
      scrollPane.setPreferredSize(new Dimension(dimension.width, dimension.height + 20));
      JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, pane).setTitle(LangBundle.message("popup.title.dependencies"))
        .setMovable(true).createPopup().showInBestPositionFor(e.getDataContext());
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      Pair<Set<PsiFile>, Set<PsiFile>> scopes = e.getUpdateSession()
        .compute(this, "getSelectedScopes", ActionUpdateThread.EDT, () -> {
          return Pair.create(getSelectedScope(myLeftTree), getSelectedScope(myRightTree));
        });
      final boolean[] direct = new boolean[]{true};
      processDependencies(scopes.first, scopes.second, path -> {
        direct [0] = false;
        return false;
      });
      e.getPresentation().setEnabled(!direct[0]);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private final class RemoveFromScopeAction extends AnAction {
    private RemoveFromScopeAction() {
      super(ActionsBundle.messagePointer("action.RemoveFromScopeAction.text"));
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!getSelectedScope(e.getData(PlatformCoreDataKeys.SELECTED_ITEMS)).isEmpty());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      final Set<PsiFile> selectedScope = getSelectedScope(myLeftTree);
      exclude(selectedScope);
      myExcluded.addAll(selectedScope);
      final TreePath[] paths = myLeftTree.getSelectionPaths();
      assert paths != null;
      for (TreePath path : paths) {
        TreeUtil.removeLastPathComponent(myLeftTree, path);
      }
    }
  }

  private final class AddToScopeAction extends AnAction {
    private AddToScopeAction() {
      super(ActionsBundle.messagePointer("action.AddToScopeAction.text"));
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      Set<PsiFile> scope = e.getUpdateSession()
        .compute(this, "getScope", ActionUpdateThread.EDT, () -> getSelectedScope(myRightTree));
      e.getPresentation().setEnabled(getScope(scope) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      final AnalysisScope scope = getScope(getSelectedScope(myRightTree));
      LOG.assertTrue(scope != null);
      final DependenciesBuilder builder;
      if (!myForward) {
        builder = new BackwardDependenciesBuilder(myProject, scope, myScopeOfInterest);
      } else {
        builder = new ForwardDependenciesBuilder(myProject, scope, myTransitiveBorder);
      }
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(myProject, CodeInsightBundle.message("package.dependencies.progress.title"),
                                                                         () -> builder.analyze(), () -> {
        myBuilders.add(builder);
        myDependencies.putAll(builder.getDependencies());
        putAllDependencies(builder);
        exclude(myExcluded);
        rebuild();
      }, null, new PerformAnalysisInBackgroundOption(myProject));
    }

    private @Nullable AnalysisScope getScope(Set<? extends PsiFile> selectedScope) {
      Set<PsiFile> result = new HashSet<>();
      ((PackageDependenciesNode)myLeftTree.getModel().getRoot()).fillFiles(result, !mySettings.UI_FLATTEN_PACKAGES);
      selectedScope.removeAll(result);
      if (selectedScope.isEmpty()) return null;
      List<VirtualFile> files = new ArrayList<>();
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      for (PsiFile psiFile : selectedScope) {
        final VirtualFile file = psiFile.getVirtualFile();
        LOG.assertTrue(file != null);
        if (fileIndex.isInContent(file)) {
          files.add(file);
        }
      }
      if (!files.isEmpty()) {
        return new AnalysisScope(myProject, files);
      }
      return null;
    }
  }

  private final class SelectInLeftTreeAction extends AnAction {
    SelectInLeftTreeAction() {
      super(CodeInsightBundle.messagePointer("action.select.in.left.tree"),
            CodeInsightBundle.messagePointer("action.select.in.left.tree.description"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      PackageDependenciesNode node = (PackageDependenciesNode)e.getData(PlatformCoreDataKeys.SELECTED_ITEM);
      e.getPresentation().setEnabled(node != null && node.canSelectInLeftTree(myDependencies));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PackageDependenciesNode node = myRightTree.getSelectedNode();
      if (node != null) {
        PsiElement elt = node.getPsiElement();
        if (elt != null) {
          DependencyUISettings.getInstance().UI_FILTER_LEGALS = false;
          mySettings.UI_FILTER_LEGALS = false;
          selectElementInLeftTree(elt);

        }
      }
    }
  }

  private void selectElementInLeftTree(PsiElement elt) {
    var pointer = SmartPointerManager.createPointer(elt);
    PsiManager manager = PsiManager.getInstance(myProject);
    TreeUtil.promiseSelect(myLeftTree, path -> {
      var object = path.getLastPathComponent();
      PsiElement element = pointer.getElement();
      if (element == null) {
        return TreeVisitor.Action.SKIP_SIBLINGS;
      }
      else if (object instanceof PackageDependenciesNode pNode && manager.areElementsEquivalent(pNode.getPsiElement(), element)) {
          return TreeVisitor.Action.INTERRUPT;
      }
      else {
        return TreeVisitor.Action.CONTINUE;
      }
    });
  }

  private final class MarkAsIllegalAction extends AnAction {
    MarkAsIllegalAction() {
      super(CodeInsightBundle.messagePointer("mark.dependency.illegal.text"),
            CodeInsightBundle.messagePointer("mark.dependency.illegal.text"), AllIcons.Actions.Lightning);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final PackageDependenciesNode leftNode = myLeftTree.getSelectedNode();
      final PackageDependenciesNode rightNode = myRightTree.getSelectedNode();
      if (leftNode != null && rightNode != null) {
        boolean hasDirectDependencies = myTransitiveBorder == 0;
        if (myTransitiveBorder > 0) {
          final Set<PsiFile> searchIn = getSelectedScope(myLeftTree);
          final Set<PsiFile> searchFor = getSelectedScope(myRightTree);
          for (DependenciesBuilder builder : myBuilders) {
            if (hasDirectDependencies) break;
            for (PsiFile from : searchIn) {
              if (hasDirectDependencies) break;
              for (PsiFile to : searchFor) {
                if (hasDirectDependencies) break;
                final List<List<PsiFile>> paths = builder.findPaths(from, to);
                for (List<PsiFile> path : paths) {
                  if (path.isEmpty()) {
                    hasDirectDependencies = true;
                    break;
                  }
                }
              }
            }
          }
        }
        final PatternDialectProvider provider = PatternDialectProvider.getInstance(mySettings.SCOPE_TYPE);
        assert provider != null;
        PackageSet leftPackageSet = provider.createPackageSet(leftNode, true);
        if (leftPackageSet == null) {
          leftPackageSet = provider.createPackageSet(leftNode, false);
        }
        LOG.assertTrue(leftPackageSet != null);
        PackageSet rightPackageSet = provider.createPackageSet(rightNode, true);
        if (rightPackageSet == null) {
          rightPackageSet = provider.createPackageSet(rightNode, false);
        }
        LOG.assertTrue(rightPackageSet != null);
        if (hasDirectDependencies) {
          DependencyValidationManager.getInstance(myProject)
            .addRule(new DependencyRule(new NamedScope.UnnamedScope(leftPackageSet),
                                        new NamedScope.UnnamedScope(rightPackageSet), true));
          rebuild();
        } else {
          Messages.showErrorDialog(DependenciesPanel.this, CodeInsightBundle
                                     .message("analyze.dependencies.unable.to.create.rule.error.message", leftPackageSet.getText(), rightPackageSet.getText()),
                                   CodeInsightBundle.message("mark.dependency.illegal.text"));
        }
      }
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      Pair<PackageDependenciesNode, PackageDependenciesNode> pair = e.getUpdateSession()
        .compute(this, "getSelectedNodes", ActionUpdateThread.EDT, () -> Pair.pair(myLeftTree.getSelectedNode(), myRightTree.getSelectedNode()));
      if (pair.first != null && pair.second != null) {
        final PatternDialectProvider provider = PatternDialectProvider.getInstance(mySettings.SCOPE_TYPE);
        assert provider != null;
        presentation.setEnabled((provider.createPackageSet(pair.first, true) != null || provider.createPackageSet(pair.first, false) != null) &&
                                (provider.createPackageSet(pair.second, true) != null || provider.createPackageSet(pair.second, false) != null));
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private final class ChooseScopeTypeAction extends ComboBoxAction {
    @Override
    protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (final PatternDialectProvider provider : PatternDialectProvider.EP_NAME.getExtensionList()) {
        group.add(new AnAction(provider.getDisplayName()) {
          @Override
          public void actionPerformed(final @NotNull AnActionEvent e) {
            mySettings.SCOPE_TYPE = provider.getShortName();
            DependencyUISettings.getInstance().SCOPE_TYPE = provider.getShortName();
            rebuild();
          }
        });
      }
      return group;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      super.update(e);
      final PatternDialectProvider provider = PatternDialectProvider.getInstance(mySettings.SCOPE_TYPE);
      assert provider != null;
      e.getPresentation().setText(provider.getDisplayName());
      e.getPresentation().setIcon(provider.getIcon());
    }
  }

  public static final class DependencyPanelSettings {
    public boolean UI_FLATTEN_PACKAGES;
    public boolean UI_SHOW_FILES;
    public boolean UI_SHOW_MODULES;
    public boolean UI_SHOW_MODULE_GROUPS;
    public boolean UI_FILTER_LEGALS;
    public boolean UI_GROUP_BY_SCOPE_TYPE;
    public String SCOPE_TYPE;
    public boolean UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    public boolean UI_FILTER_OUT_OF_CYCLE_PACKAGES;

    public DependencyPanelSettings() {
      final DependencyUISettings settings = DependencyUISettings.getInstance();
      UI_FLATTEN_PACKAGES = settings.UI_FLATTEN_PACKAGES;
      UI_SHOW_FILES = settings.UI_SHOW_FILES;
      UI_SHOW_MODULES = settings.UI_SHOW_MODULES;
      UI_SHOW_MODULE_GROUPS = settings.UI_SHOW_MODULE_GROUPS;
      UI_FILTER_LEGALS = settings.UI_FILTER_LEGALS;
      UI_GROUP_BY_SCOPE_TYPE = settings.UI_GROUP_BY_SCOPE_TYPE;
      SCOPE_TYPE = settings.SCOPE_TYPE;
      UI_COMPACT_EMPTY_MIDDLE_PACKAGES = settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
      UI_FILTER_OUT_OF_CYCLE_PACKAGES = settings.UI_FILTER_OUT_OF_CYCLE_PACKAGES;
    }

    public void copyToApplicationDependencySettings(){
      final DependencyUISettings settings = DependencyUISettings.getInstance();
      settings.UI_FLATTEN_PACKAGES = UI_FLATTEN_PACKAGES;
      settings.UI_SHOW_FILES = UI_SHOW_FILES;
      settings.UI_SHOW_MODULES = UI_SHOW_MODULES;
      settings.UI_SHOW_MODULE_GROUPS = UI_SHOW_MODULE_GROUPS;
      settings.UI_FILTER_LEGALS = UI_FILTER_LEGALS;
      settings.UI_GROUP_BY_SCOPE_TYPE = UI_GROUP_BY_SCOPE_TYPE;
      settings.SCOPE_TYPE = SCOPE_TYPE;
      settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
      settings.UI_FILTER_OUT_OF_CYCLE_PACKAGES = UI_FILTER_OUT_OF_CYCLE_PACKAGES;
    }
  }
}