/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.packageDependencies.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.packageDependencies.*;
import com.intellij.packageDependencies.actions.AnalyzeDependenciesHandler;
import com.intellij.packageDependencies.actions.BackwardDependenciesHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.*;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DependenciesPanel extends JPanel implements Disposable, DataProvider {
  private final Map<PsiFile, Set<PsiFile>> myDependencies;
  private Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> myIllegalDependencies;
  private final MyTree myLeftTree = new MyTree();
  private final MyTree myRightTree = new MyTree();
  private final DependenciesUsagesPanel myUsagesPanel;

  private static final HashSet<PsiFile> EMPTY_FILE_SET = new HashSet<PsiFile>(0);
  private final TreeExpansionMonitor myRightTreeExpansionMonitor;
  private final TreeExpansionMonitor myLeftTreeExpansionMonitor;

  private final Marker myRightTreeMarker;
  private final Marker myLeftTreeMarker;
  private Set<PsiFile> myIllegalsInRightTree = new HashSet<PsiFile>();

  private final Project myProject;
  private final List<DependenciesBuilder> myBuilders;
  private final Set<PsiFile> myExcluded;
  private Content myContent;
  private final DependencyPanelSettings mySettings = new DependencyPanelSettings();
  private static final Logger LOG = Logger.getInstance("#" + DependenciesPanel.class.getName());

  private final boolean myForward;
  private final AnalysisScope myScopeOfInterest;
  private final int myTransitiveBorder;

  public DependenciesPanel(Project project, final DependenciesBuilder builder){
    this(project, Collections.singletonList(builder), new HashSet<PsiFile>());
  }

  public DependenciesPanel(Project project, final List<DependenciesBuilder> builders, final Set<PsiFile> excluded) {
    super(new BorderLayout());
    myBuilders = builders;
    myExcluded = excluded;
    final DependenciesBuilder main = myBuilders.get(0);
    myForward = !main.isBackward();
    myScopeOfInterest = main.getScopeOfInterest();
    myTransitiveBorder = main.getTransitiveBorder();
    myDependencies = new HashMap<PsiFile, Set<PsiFile>>();
    myIllegalDependencies = new HashMap<PsiFile, Map<DependencyRule, Set<PsiFile>>>();
    for (DependenciesBuilder builder : builders) {
      myDependencies.putAll(builder.getDependencies());
      myIllegalDependencies.putAll(builder.getIllegalDependencies());
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
    add(createToolbar(), BorderLayout.NORTH);

    myRightTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myRightTree, myProject);
    myLeftTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myLeftTree, myProject);

    myRightTreeMarker = new Marker() {
      @Override
      public boolean isMarked(VirtualFile file) {
        return myIllegalsInRightTree.contains(file);
      }
    };

    myLeftTreeMarker = new Marker() {
      @Override
      public boolean isMarked(VirtualFile file) {
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
          StatusBar.Info.set(AnalysisScopeBundle.message("status.bar.rule.violation.message",
                                                        ((denyRules.length() == 0 || allowRules.length() == 0) ? 1 : 2),
                                                        (denyRules.length() > 0 ? denyRules.toString() + (allowRules.length() > 0 ? "; " : "") : " ") +
                                                        (allowRules.length() > 0 ? allowRules.toString() : " ")), myProject);
        }
        else {
          StatusBar.Info.set(AnalysisScopeBundle.message("status.bar.no.rule.violation.message"), myProject);
        }
      }
    });

    myRightTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final Set<PsiFile> searchIn = getSelectedScope(myLeftTree);
            final Set<PsiFile> searchFor = getSelectedScope(myRightTree);
            if (searchIn.isEmpty() || searchFor.isEmpty()) {
              myUsagesPanel.setToInitialPosition();
              processDependencies(searchIn, searchFor, new Processor<List<PsiFile>>() { //todo do not show too many usages
                @Override
                public boolean process(final List<PsiFile> path) {
                  searchFor.add(path.get(1));
                  return true;
                }
              });
            }
            else {
              myUsagesPanel.findUsages(searchIn, searchFor);
            }
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
    TreeUtil.selectFirstNode(myLeftTree);
  }

  private void processDependencies(final Set<PsiFile> searchIn, final Set<PsiFile> searchFor, Processor<List<PsiFile>> processor) {
    if (myTransitiveBorder == 0) return;
    Set<PsiFile> initialSearchFor = new HashSet<PsiFile>(searchFor);
    for (DependenciesBuilder builder : myBuilders) {
      for (PsiFile from : searchIn) {
        for (PsiFile to : initialSearchFor) {
          final List<List<PsiFile>> paths = builder.findPaths(from, to);
          Collections.sort(paths, new Comparator<List<PsiFile>>() {
            @Override
            public int compare(final List<PsiFile> p1, final List<PsiFile> p2) {
              return p1.size() - p2.size();
            }
          });
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

  private void exclude(final Set<PsiFile> excluded) {
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

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseAction());
    group.add(new RerunAction(this));
    group.add(new FlattenPackagesAction());
    group.add(new ShowFilesAction());
    if (ModuleManager.getInstance(myProject).getModules().length > 1) {
      group.add(new ShowModulesAction());
      group.add(new ShowModuleGroupsAction());
    }
    group.add(new GroupByScopeTypeAction());
    //group.add(new GroupByFilesAction());
    group.add(new FilterLegalsAction());
    group.add(new MarkAsIllegalAction());
    group.add(new ChooseScopeTypeAction());
    group.add(new EditDependencyRulesAction());
    group.add(CommonActionsManager.getInstance().createExportToTextFileAction(new DependenciesExporterToTextFile()));
    group.add(new ContextHelpAction("dependency.viewer.tool.window"));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  private void rebuild() {
    myIllegalDependencies = new HashMap<PsiFile, Map<DependencyRule, Set<PsiFile>>>();
    for (DependenciesBuilder builder : myBuilders) {
      myIllegalDependencies.putAll(builder.getIllegalDependencies());
    }
    updateLeftTreeModel();
    updateRightTreeModel();
  }

  private void initTree(final MyTree tree, boolean isRightTree) {
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    new TreeSpeedSearch(tree);

    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(isRightTree), ActionManager.getInstance());


  }

  private void updateRightTreeModel() {
    Set<PsiFile> deps = new HashSet<PsiFile>();
    Set<PsiFile> scope = getSelectedScope(myLeftTree);
    myIllegalsInRightTree = new HashSet<PsiFile>();
    for (PsiFile psiFile : scope) {
      Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(psiFile);
      if (illegalDeps != null) {
        for (final DependencyRule rule : illegalDeps.keySet()) {
          myIllegalsInRightTree.addAll(illegalDeps.get(rule));
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
    myRightTreeExpansionMonitor.freeze();
    myRightTree.setModel(buildTreeModel(deps, myRightTreeMarker));
    myRightTreeExpansionMonitor.restore();
    expandFirstLevel(myRightTree);
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

  private TreeModel buildTreeModel(Set<PsiFile> deps, Marker marker) {
    return PatternDialectProvider.getInstance(mySettings.SCOPE_TYPE).createTreeModel(myProject, deps, marker,
                                                                                                             mySettings);
  }

  private void updateLeftTreeModel() {
    Set<PsiFile> psiFiles = myDependencies.keySet();
    myLeftTreeExpansionMonitor.freeze();
    myLeftTree.setModel(buildTreeModel(psiFiles, myLeftTreeMarker));
    myLeftTreeExpansionMonitor.restore();
    expandFirstLevel(myLeftTree);
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
    //another level of nesting
    if (count == 1 && node.getChildAt(0).getChildCount() > 5){
      return;
    }
    tree.expandPath(new TreePath(node.getPath()));
  }

  private Set<PsiFile> getSelectedScope(final Tree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null ) return EMPTY_FILE_SET;
    Set<PsiFile> result = new HashSet<PsiFile>();
    for (TreePath path : paths) {
      PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
      node.fillFiles(result, !mySettings.UI_FLATTEN_PACKAGES);
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
  @Nullable
  @NonNls
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final PackageDependenciesNode selectedNode = myRightTree.getSelectedNode();
      if (selectedNode != null) {
        final PsiElement element = selectedNode.getPsiElement();
        return element != null && element.isValid() ? element : null;
      }
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return "dependency.viewer.tool.window";
    }
    return null;
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(
    JTree tree,
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
    public CloseAction() {
      super(CommonBundle.message("action.close"), AnalysisScopeBundle.message("action.close.dependency.description"),
            AllIcons.Actions.Cancel);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Disposer.dispose(myUsagesPanel);
      DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
    }
  }

  private final class FlattenPackagesAction extends ToggleAction {
    FlattenPackagesAction() {
      super(AnalysisScopeBundle.message("action.flatten.packages"),
            AnalysisScopeBundle.message("action.flatten.packages"),
            PlatformIcons.FLATTEN_PACKAGES_ICON);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_FLATTEN_PACKAGES;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES = flag;
      mySettings.UI_FLATTEN_PACKAGES = flag;
      rebuild();
    }
  }

  private final class ShowFilesAction extends ToggleAction {
    ShowFilesAction() {
      super(AnalysisScopeBundle.message("action.show.files"), AnalysisScopeBundle.message("action.show.files.description"),
            AllIcons.FileTypes.Unknown);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_SHOW_FILES;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
      mySettings.UI_SHOW_FILES = flag;
      if (!flag && myLeftTree.getSelectionPath() != null && myLeftTree.getSelectionPath().getLastPathComponent() instanceof FileNode){
        TreeUtil.selectPath(myLeftTree, myLeftTree.getSelectionPath().getParentPath());
      }
      rebuild();
    }
  }

 /* private final class GroupByFilesAction extends ToggleAction {
    private GroupByFilesAction() {
      super(IdeBundle.message("action.show.file.structure"),
            IdeBundle.message("action.description.show.file.structure"), IconLoader.getIcon("/objectBrowser/showGlobalInspections.png"));
    }

    public boolean isSelected(final AnActionEvent e) {
      return mySettings.SCOPE_TYPE;
    }

    public void setSelected(final AnActionEvent e, final boolean state) {
      mySettings.SCOPE_TYPE = state;
      mySettings.copyToApplicationDependencySettings();
      rebuild();
    }
  }*/

  private final class ShowModulesAction extends ToggleAction {
    ShowModulesAction() {
      super(AnalysisScopeBundle.message("action.show.modules"), AnalysisScopeBundle.message("action.show.modules.description"),
            AllIcons.Actions.GroupByModule);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_SHOW_MODULES;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_MODULES = flag;
      mySettings.UI_SHOW_MODULES = flag;
      rebuild();
    }
  }

  private final class ShowModuleGroupsAction extends ToggleAction {
    ShowModuleGroupsAction() {
      super("Show module groups", "Show module groups", AllIcons.Actions.GroupByModuleGroup);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_SHOW_MODULE_GROUPS;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS = flag;
      mySettings.UI_SHOW_MODULE_GROUPS = flag;
      rebuild();
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(mySettings.UI_SHOW_MODULES);
    }
  }

  private final class GroupByScopeTypeAction extends ToggleAction {
    GroupByScopeTypeAction() {
      super(AnalysisScopeBundle.message("action.group.by.scope.type"), AnalysisScopeBundle.message("action.group.by.scope.type.description"),
            AllIcons.Actions.GroupByTestProduction);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_GROUP_BY_SCOPE_TYPE;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = flag;
      rebuild();
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
    }
  }


  private final class FilterLegalsAction extends ToggleAction {
    FilterLegalsAction() {
      super(AnalysisScopeBundle.message("action.show.illegals.only"), AnalysisScopeBundle.message("action.show.illegals.only.description"),
            AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_FILTER_LEGALS;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
      mySettings.UI_FILTER_LEGALS = flag;
      setEmptyText(flag);
      rebuild();
    }
  }

  private void setEmptyText(boolean flag) {
    final String emptyText = flag ? "No illegal dependencies found" : "Nothing to show";
    myLeftTree.getEmptyText().setText(emptyText);
    myRightTree.getEmptyText().setText(emptyText);
  }

  private final class EditDependencyRulesAction extends AnAction {
    public EditDependencyRulesAction() {
      super(AnalysisScopeBundle.message("action.edit.rules"), AnalysisScopeBundle.message("action.edit.rules.description"),
            AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      boolean applied = ShowSettingsUtil.getInstance().editConfigurable(DependenciesPanel.this, new DependencyConfigurable(myProject));
      if (applied) {
        rebuild();
      }
    }
  }


  private class DependenciesExporterToTextFile implements ExporterToTextFile {

    @Override
    public JComponent getSettingsEditor() {
      return null;
    }

    @Override
    public void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException {
    }

    @Override
    public void removeSettingsChangedListener(ChangeListener listener) {
    }

    @Override
    public String getReportText() {
      final Element rootElement = new Element("root");
      rootElement.setAttribute("isBackward", String.valueOf(!myForward));
      final List<PsiFile> files = new ArrayList<PsiFile>(myDependencies.keySet());
      Collections.sort(files, new Comparator<PsiFile>() {
        @Override
        public int compare(PsiFile f1, PsiFile f2) {
          final VirtualFile virtualFile1 = f1.getVirtualFile();
          final VirtualFile virtualFile2 = f2.getVirtualFile();
          if (virtualFile1 != null && virtualFile2 != null) {
            return virtualFile1.getPath().compareToIgnoreCase(virtualFile2.getPath());
          }
          return 0;
        }
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
      PathMacroManager.getInstance(myProject).collapsePaths(rootElement);
      return JDOMUtil.writeDocument(new Document(rootElement), SystemProperties.getLineSeparator());
    }

    @Override
    public String getDefaultFilePath() {
      return "";
    }

    @Override
    public void exportedTo(String filePath) {
    }

    @Override
    public boolean canExport() {
      return true;
    }
  }


  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super(CommonBundle.message("action.rerun"), AnalysisScopeBundle.message("action.rerun.dependency"), AllIcons.Actions.Rerun);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    @Override
    public void update(AnActionEvent e) {
      boolean enabled = true;
      for (DependenciesBuilder builder : myBuilders) {
        enabled &= builder.getScope().isValid();
      }
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          final List<AnalysisScope> scopes = new ArrayList<AnalysisScope>();
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
        }
      });
    }
  }

  private static class MyTree extends Tree implements DataProvider {
    @Override
    public Object getData(String dataId) {
      PackageDependenciesNode node = getSelectedNode();
      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        return node;
      }
      if (CommonDataKeys.PSI_ELEMENT.is(dataId) && node != null)  {
        final PsiElement element = node.getPsiElement();
        return element != null && element.isValid() ? element : null;
      }
      return null;
    }

    @Nullable
    public PackageDependenciesNode getSelectedNode() {
      TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length != 1) return null;
      return (PackageDependenciesNode)paths[0].getLastPathComponent();
    }
  }

  private class ShowDetailedInformationAction extends AnAction {
    private ShowDetailedInformationAction() {
      super("Show indirect dependencies");
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      @NonNls final String delim = "&nbsp;-&gt;&nbsp;";
      final StringBuffer buf = new StringBuffer();
      processDependencies(getSelectedScope(myLeftTree), getSelectedScope(myRightTree), new Processor<List<PsiFile>>() {
        @Override
        public boolean process(final List<PsiFile> path) {
          if (buf.length() > 0) buf.append("<br>");
          buf.append(StringUtil.join(path, new Function<PsiFile, String>() {
            @Override
            public String fun(final PsiFile psiFile) {
              return psiFile.getName();
            }
          }, delim));
          return true;
        }
      });
      final JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, XmlStringUtil.wrapInHtml(buf));
      pane.setForeground(JBColor.foreground());
      pane.setBackground(HintUtil.INFORMATION_COLOR);
      pane.setOpaque(true);
      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
      final Dimension dimension = pane.getPreferredSize();
      scrollPane.setMinimumSize(new Dimension(dimension.width, dimension.height + 20));
      scrollPane.setPreferredSize(new Dimension(dimension.width, dimension.height + 20));
      JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, pane).setTitle("Dependencies")
        .setMovable(true).createPopup().showInBestPositionFor(e.getDataContext());
    }

    @Override
    public void update(final AnActionEvent e) {
      final boolean[] direct = new boolean[]{true};
      processDependencies(getSelectedScope(myLeftTree), getSelectedScope(myRightTree), new Processor<List<PsiFile>>() {
        @Override
        public boolean process(final List<PsiFile> path) {
          direct [0] = false;
          return false;
        }
      });
      e.getPresentation().setEnabled(!direct[0]);
    }
  }

  private class RemoveFromScopeAction extends AnAction {
    private RemoveFromScopeAction() {
      super("Remove from scope");
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!getSelectedScope(myLeftTree).isEmpty());
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final Set<PsiFile> selectedScope = getSelectedScope(myLeftTree);
      exclude(selectedScope);
      myExcluded.addAll(selectedScope);
      final TreePath[] paths = myLeftTree.getSelectionPaths();
      for (TreePath path : paths) {
        TreeUtil.removeLastPathComponent(myLeftTree, path);
      }
    }
  }

  private class AddToScopeAction extends AnAction {
    private AddToScopeAction() {
      super("Add to scope");
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(getScope() != null);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final AnalysisScope scope = getScope();
      LOG.assertTrue(scope != null);
      final DependenciesBuilder builder;
      if (!myForward) {
        builder = new BackwardDependenciesBuilder(myProject, scope, myScopeOfInterest);
      } else {
        builder = new ForwardDependenciesBuilder(myProject, scope, myTransitiveBorder);
      }
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(myProject, AnalysisScopeBundle.message("package.dependencies.progress.title"), new Runnable() {
        @Override
        public void run() {
          builder.analyze();
        }
      }, new Runnable() {
        @Override
        public void run() {
          myBuilders.add(builder);
          myDependencies.putAll(builder.getDependencies());
          myIllegalDependencies.putAll(builder.getIllegalDependencies());
          exclude(myExcluded);
          rebuild();
        }
      }, null, new PerformAnalysisInBackgroundOption(myProject));
    }

    @Nullable
    private AnalysisScope getScope() {
      final Set<PsiFile> selectedScope = getSelectedScope(myRightTree);
      Set<PsiFile> result = new HashSet<PsiFile>();
      ((PackageDependenciesNode)myLeftTree.getModel().getRoot()).fillFiles(result, !mySettings.UI_FLATTEN_PACKAGES);
      selectedScope.removeAll(result);
      if (selectedScope.isEmpty()) return null;
      List<VirtualFile> files = new ArrayList<VirtualFile>();
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

  private class SelectInLeftTreeAction extends AnAction {
    public SelectInLeftTreeAction() {
      super(AnalysisScopeBundle.message("action.select.in.left.tree"), AnalysisScopeBundle.message("action.select.in.left.tree.description"), null);
    }

    @Override
    public void update(AnActionEvent e) {
      PackageDependenciesNode node = myRightTree.getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.canSelectInLeftTree(myDependencies));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
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
    PsiManager manager = PsiManager.getInstance(myProject);

    PackageDependenciesNode root = (PackageDependenciesNode)myLeftTree.getModel().getRoot();
    Enumeration enumeration = root.breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      PackageDependenciesNode child = (PackageDependenciesNode)enumeration.nextElement();
      if (manager.areElementsEquivalent(child.getPsiElement(), elt)) {
        myLeftTree.setSelectionPath(new TreePath(((DefaultTreeModel)myLeftTree.getModel()).getPathToRoot(child)));
        break;
      }
    }
  }

  private class MarkAsIllegalAction extends AnAction {
    public MarkAsIllegalAction() {
      super(AnalysisScopeBundle.message("mark.dependency.illegal.text"), AnalysisScopeBundle.message("mark.dependency.illegal.text"),
            AllIcons.Actions.Lightning);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
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
          Messages.showErrorDialog(DependenciesPanel.this, "Rule was not added.\n There is no direct dependency between \'" + leftPackageSet.getText() + "\' and \'" + rightPackageSet.getText() + "\'",
                                   AnalysisScopeBundle.message("mark.dependency.illegal.text"));
        }
      }
    }

    @Override
    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final PackageDependenciesNode leftNode = myLeftTree.getSelectedNode();
      final PackageDependenciesNode rightNode = myRightTree.getSelectedNode();
      if (leftNode != null && rightNode != null) {
        final PatternDialectProvider provider = PatternDialectProvider.getInstance(mySettings.SCOPE_TYPE);
        presentation.setEnabled((provider.createPackageSet(leftNode, true) != null || provider.createPackageSet(leftNode, false) != null) &&
                                (provider.createPackageSet(rightNode, true) != null || provider.createPackageSet(rightNode, false) != null));
      }
    }
  }

  private final class ChooseScopeTypeAction extends ComboBoxAction {
    @Override
    @NotNull
    protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (final PatternDialectProvider provider : Extensions.getExtensions(PatternDialectProvider.EP_NAME)) {
        group.add(new AnAction(provider.getDisplayName()) {
          @Override
          public void actionPerformed(final AnActionEvent e) {
            mySettings.SCOPE_TYPE = provider.getShortName();
            DependencyUISettings.getInstance().SCOPE_TYPE = provider.getShortName();
            rebuild();
          }
        });
      }
      return group;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final PatternDialectProvider provider = PatternDialectProvider.getInstance(mySettings.SCOPE_TYPE);
      e.getPresentation().setText(provider.getDisplayName());
      e.getPresentation().setIcon(provider.getIcon());
    }
  }

  public static class DependencyPanelSettings {
    public boolean UI_FLATTEN_PACKAGES = true;
    public boolean UI_SHOW_FILES = false;
    public boolean UI_SHOW_MODULES = true;
    public boolean UI_SHOW_MODULE_GROUPS = true;
    public boolean UI_FILTER_LEGALS = false;
    public boolean UI_GROUP_BY_SCOPE_TYPE = true;
    public String SCOPE_TYPE;
    public boolean UI_COMPACT_EMPTY_MIDDLE_PACKAGES = true;
    public boolean UI_FILTER_OUT_OF_CYCLE_PACKAGES = true;

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
