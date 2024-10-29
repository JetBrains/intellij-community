// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.lang.LangBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectBasedInspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.filter.InspectionFilterAction;
import com.intellij.profile.codeInspection.ui.filter.InspectionsFilter;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeRenderer;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeTable;
import com.intellij.profile.codeInspection.ui.table.ScopesAndSeveritiesTable;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.ui.treeStructure.treetable.DefaultTreeTableExpander;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.*;

public class SingleInspectionProfilePanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(SingleInspectionProfilePanel.class);
  private static final @NonNls String INSPECTION_FILTER_HISTORY = "INSPECTION_FILTER_HISTORY";

  private static final float DIVIDER_PROPORTION_DEFAULT = 0.5f;
  private static final int SECTION_GAP = 20;

  private final Map<String, ToolDescriptors> myInitialToolDescriptors = new HashMap<>();
  private final InspectionConfigTreeNode myRoot = new InspectionConfigTreeNode.Group(InspectionsBundle.message("inspection.root.node.title"));
  private final List<InspectionTreeAdvertiser.CustomGroup> myCustomGroups = new ArrayList<>();
  private final Alarm myAlarm = new Alarm();
  private final ProjectInspectionProfileManager myProjectProfileManager;
  private final @NotNull InspectionProfileModifiableModel myProfile;
  private DescriptionEditorPane myDescription;
  private JBLabel myOptionsLabel;
  private JPanel myOptionsPanel;
  private JPanel myInspectionProfilePanel;
  private FilterComponent myProfileFilter;
  private final InspectionsFilter myInspectionsFilter = new InspectionsFilter() {
    @Override
    protected void filterChanged() {
      filterTree();
      updateEmptyText();
    }
  };
  private boolean myModified;
  private InspectionsConfigTreeTable myTreeTable;
  private TreeExpander myTreeExpander;
  private boolean myIsInRestore;
  private final DefaultActionGroup myCreateInspectionActions;

  private List<String> myInitialScopesOrder;
  private Disposable myDisposable = Disposer.newDisposable();
  private ToolOptionsSeparator myOptionsSeparator;

  public SingleInspectionProfilePanel(@NotNull ProjectInspectionProfileManager projectProfileManager,
                                      @NotNull InspectionProfileModifiableModel profile) {
    super(new BorderLayout());
    myProjectProfileManager = projectProfileManager;
    myProfile = profile;

    myCustomGroups.add(new InspectionTreeAdvertiser.CustomGroup(
      new String[]{InspectionsBundle.message("group.names.user.defined")},
      InspectionsBundle.message("group.descriptions.user.defined")
    ));
    for (InspectionTreeAdvertiser advertiser : InspectionTreeAdvertiser.EP_NAME.getExtensionList()) {
      myCustomGroups.addAll(advertiser.getCustomGroups());
    }
    myCreateInspectionActions = CustomInspectionActions.getAddActionGroup(this);
  }

  public boolean differsFromDefault() {
    return myRoot.isProperSetting();
  }

  public void performProfileReset() {
    //forcibly initialize configs to be able compare xmls after reset
    TreeUtil.treeNodeTraverser(myRoot).traverse().processEach(n -> {
      InspectionConfigTreeNode node = (InspectionConfigTreeNode)n;
      if (node instanceof InspectionConfigTreeNode.Tool && node.isProperSetting()) {
        ((InspectionConfigTreeNode.Tool)node).getDefaultDescriptor().loadConfig();
      }
      return true;
    });
    getProfile().resetToBase(getProject());
    loadDescriptorsConfigs(true);
    postProcessModification();
    updateModificationMarker();
    myRoot.dropCache();
  }

  public @NotNull Project getProject() {
    return myProjectProfileManager.getProject();
  }

  private static VisibleTreeState getExpandedNodes(InspectionProfileImpl profile) {
    if (profile.isProjectLevel()) {
      return ProjectInspectionProfilesVisibleTreeState.getInstance(((ProjectBasedInspectionProfileManager)profile.getProfileManager()).getProject()).getVisibleTreeState(profile);
    }
    return AppInspectionProfilesVisibleTreeState.getInstance().getVisibleTreeState(profile);
  }

  private static InspectionConfigTreeNode findGroupNodeByPath(String @NotNull [] path, int idx, @NotNull InspectionConfigTreeNode node) {
    if (path.length == idx) {
      return node;
    }

    final String currentKey = path[idx];
    for (int i = 0; i < node.getChildCount(); i++) {
      final InspectionConfigTreeNode currentNode = (InspectionConfigTreeNode)node.getChildAt(i);
      if (currentNode instanceof InspectionConfigTreeNode.Group && ((InspectionConfigTreeNode.Group)currentNode).getGroupName().equals(currentKey)) {
        return findGroupNodeByPath(path, ++idx, currentNode);
      }
    }

    return null;
  }

  private static @Nullable InspectionConfigTreeNode findNodeByKey(String name, InspectionConfigTreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      final InspectionConfigTreeNode child = (InspectionConfigTreeNode)root.getChildAt(i);
      if (child instanceof InspectionConfigTreeNode.Tool) {
        if (((InspectionConfigTreeNode.Tool)child).getKey().getShortName().equals(name)) {
          return child;
        }
      }
      else {
        final InspectionConfigTreeNode node = findNodeByKey(name, child);
        if (node != null) return node;
      }
    }
    return null;
  }

  public static @Nls String renderSeverity(@NotNull HighlightSeverity severity) {
    if (HighlightSeverity.INFORMATION.equals(severity)) return LangBundle.message("single.inspection.profile.panel.no.highlighting.only.fix");
    return severity.getDisplayCapitalizedName();
  }

  private static boolean isDescriptorAccepted(Descriptor descriptor,
                                              @NonNls String filter,
                                              final boolean forceInclude,
                                              final List<? extends Set<String>> keySetList, final Set<String> quoted) {
    filter = StringUtil.toLowerCase(filter);
    if (StringUtil.containsIgnoreCase(descriptor.getText(), filter)) {
      return true;
    }
    final String[] groupPath = descriptor.getGroup();
    for (String group : groupPath) {
      if (StringUtil.containsIgnoreCase(group, filter)) {
        return true;
      }
    }
    for (String stripped : quoted) {
      if (StringUtil.containsIgnoreCase(descriptor.getText(),stripped)) {
        return true;
      }
      for (String group : groupPath) {
        if (StringUtil.containsIgnoreCase(group,stripped)) {
          return true;
        }
      }
      final String description = descriptor.getToolWrapper().loadDescription();
      if (description != null && StringUtil.containsIgnoreCase(StringUtil.toLowerCase(description), stripped)) {
        if (!forceInclude) return true;
      } else if (forceInclude) return false;
    }
    for (Set<String> keySet : keySetList) {
      if (keySet.contains(descriptor.getKey().getShortName())) {
        if (!forceInclude) {
          return true;
        }
      }
      else {
        if (forceInclude) {
          return false;
        }
      }
    }
    return forceInclude;
  }

  private static boolean isCustomGroupAccepted(InspectionTreeAdvertiser.CustomGroup customGroup,
                                               @NonNls String filter,
                                               final Set<String> quoted) {
    final String[] path = customGroup.path();
    if (StringUtil.containsIgnoreCase(path[path.length - 1], filter)) return true;

    final String description = customGroup.description();
    if (ContainerUtil.all(filter.split(" "), part -> StringUtil.containsIgnoreCase(description, part))) return true;

    for (String stripped : quoted) {
      if (StringUtil.containsIgnoreCase(path[path.length - 1], stripped)) return true;
      if (StringUtil.containsIgnoreCase(description, stripped)) return true;
    }

    return false;
  }

  private void setConfigPanel(final JPanel configPanelAnchor, final ScopeToolState state) {
    configPanelAnchor.removeAll();
    final JComponent additionalConfigPanel = state.getAdditionalConfigPanel(myDisposable, getProject());
    if (additionalConfigPanel != null) {
      additionalConfigPanel.setBorder(InspectionUiUtilKt.getBordersForOptions(additionalConfigPanel));
      configPanelAnchor.add(
        InspectionUiUtilKt.addScrollPaneIfNecessary(additionalConfigPanel),
        new GridBag().next().weightx(1.0).weighty(1.0).fillCell().anchor(GridBagConstraints.NORTHWEST)
      );
    }

    if (myOptionsLabel != null)
      myOptionsLabel.setText(
        AnalysisBundle.message("inspections.settings.options.title.specific.scope",
                               state.getScopeName().equals(CustomScopesProviderEx.getAllScope().getScopeId())
                                 ? LangBundle.message("scopes.table.everywhere.else")
                                 : state.getScopeName()));
  }

  private static InspectionConfigTreeNode getGroupNode(InspectionConfigTreeNode root, String[] groupPath) {
    InspectionConfigTreeNode currentRoot = root;
    for (final String group : groupPath) {
      currentRoot = getGroupNode(currentRoot, group);
    }
    return currentRoot;
  }

  private static InspectionConfigTreeNode getGroupNode(InspectionConfigTreeNode root, String group) {
    final int childCount = root.getChildCount();
    for (int i = 0; i < childCount; i++) {
      InspectionConfigTreeNode child = (InspectionConfigTreeNode)root.getChildAt(i);
      if (group.equals(child.getUserObject())) {
        return child;
      }
    }
    InspectionConfigTreeNode child = new InspectionConfigTreeNode.Group(group);
    root.add(child);
    return child;
  }

  private static void copyUsedSeveritiesIfUndefined(InspectionProfileImpl selectedProfile, BaseInspectionProfileManager profileManager) {
    final SeverityRegistrar registrar = profileManager.getSeverityRegistrar();
    final Set<HighlightSeverity> severities = selectedProfile.getUsedSeverities();
    severities.removeIf(severity -> registrar.isSeverityValid(severity.getName()));

    if (!severities.isEmpty()) {
      final SeverityRegistrar oppositeRegister = selectedProfile.getProfileManager().getSeverityRegistrar();
      for (HighlightSeverity severity : severities) {
        final TextAttributesKey attributesKey = TextAttributesKey.find(severity.getName());
        final TextAttributes textAttributes = oppositeRegister.getTextAttributesBySeverity(severity);
        if (textAttributes == null) {
          continue;
        }
        HighlightInfoType.HighlightInfoTypeImpl info = new HighlightInfoType.HighlightInfoTypeImpl(severity, attributesKey);
        registrar.registerSeverity(new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes.clone(), info),
                                   textAttributes.getErrorStripeColor());
      }
    }
  }

  private void initUI() {
    myInspectionProfilePanel = createInspectionProfileSettingsPanel();
    add(myInspectionProfilePanel, BorderLayout.CENTER);
    UserActivityWatcher userActivityWatcher = new UserActivityWatcher();
    userActivityWatcher.addUserActivityListener(() -> {
      //invoke after all other listeners
      ApplicationManager.getApplication().invokeLater(() -> {
        if (isDisposed()) return; //panel was disposed
        updateProperSettingsForSelection();
        updateModificationMarker();
      });
    });
    userActivityWatcher.register(myOptionsPanel);
    updateSelectedProfileState();
    reset();
    getProject().getMessageBus().connect(myDisposable).subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
      @Override
      public void profileChanged(@NotNull InspectionProfile profile) {
        if (myProfile == profile) {
          initToolStates();
          filterTree();
        }
      }
    });
  }

  private void updateSelectedProfileState() {
    if (isDisposed()) return;
    restoreTreeState();
    repaintTableData();
    updateSelection();
  }

  public void updateSelection() {
    if (myTreeTable != null) {
      final TreePath selectionPath = myTreeTable.getTree().getSelectionPath();
      if (selectionPath != null) {
        TreeUtil.selectNode(myTreeTable.getTree(), (TreeNode) selectionPath.getLastPathComponent());
        final int rowForPath = myTreeTable.getTree().getRowForPath(selectionPath);
        TableUtil.selectRows(myTreeTable, new int[]{rowForPath});
        scrollToCenter();
      }
    }
  }

  private void loadDescriptorsConfigs(boolean onlyModified) {
    myInitialToolDescriptors.values().stream().flatMap(ToolDescriptors::getDescriptors).forEach(d -> {
      if (!onlyModified || myProfile.isProperSetting(d.getKey().getShortName())) {
        d.loadConfig();
      }
    });
  }

  private void updateModificationMarker() {
    myModified = myInitialToolDescriptors.values().stream().flatMap(ToolDescriptors::getDescriptors).anyMatch(descriptor -> {
      Element oldConfig = descriptor.getConfig();
      if (oldConfig == null) return false;
      ScopeToolState state = descriptor.getState();
      Element newConfig = Descriptor.createConfigElement(state.getTool());
      if (!JDOMUtil.areElementsEqual(oldConfig, newConfig)) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> {
          myTreeTable.repaint();
          if (myOptionsSeparator != null) myOptionsSeparator.setupResetLinkVisibility();
        }, 300);
        return true;
      }
      return false;
    });
  }

  private void updateProperSettingsForSelection() {
    final TreePath selectionPath = myTreeTable.getTree().getSelectionPath();
    if (selectionPath != null) {
      InspectionConfigTreeNode node = (InspectionConfigTreeNode)selectionPath.getLastPathComponent();
      if (node instanceof InspectionConfigTreeNode.Tool) {
        final boolean properSetting = myProfile.isProperSetting(((InspectionConfigTreeNode.Tool)node).getKey().getShortName());
        if (node.isProperSetting() != properSetting) {
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(() -> myTreeTable.repaint(), 300);
          InspectionConfigTreeNode.updateUpHierarchy(node);
        }
      }
    }
  }

  private void initToolStates() {
    if (isDisposed()) return;

    myInitialToolDescriptors.clear();
    final Project project = getProject();
    for (final ScopeToolState state : myProfile.getDefaultStates(getProject())) {
      if (!accept(state.getTool())) {
        continue;
      }
      ToolDescriptors descriptors = ToolDescriptors.fromScopeToolState(state, myProfile, project);
      myInitialToolDescriptors.put(descriptors.getDefaultDescriptor().getShortName(), descriptors);
    }
    myInitialScopesOrder = myProfile.getScopesOrder();
  }

  private boolean isDisposed() {
    return myDisposable == null;
  }

  protected boolean accept(InspectionToolWrapper entry) {
    return !entry.getDefaultLevel().isNonSwitchable();
  }

  private void postProcessModification() {
    updateModificationMarker();
    //resetup configs
    for (ScopeToolState state : myProfile.getAllTools()) {
      state.resetConfigPanel();
    }
    fillTreeData(myProfileFilter.getFilter(), true);
    repaintTableData();
    updateOptionsAndDescriptionPanel();
  }

  public void setFilter(String filter) {
    myProfileFilter.setFilter(filter);
  }

  private void filterTree() {
    String filter = myProfileFilter != null ? myProfileFilter.getFilter() : null;
    if (myTreeTable != null) {
      getExpandedNodes(myProfile).saveVisibleState(myTreeTable.getTree());
      fillTreeData(filter, true);
      reloadModel();
      restoreTreeState();
      if (myTreeTable.getTree().getSelectionPath() == null) {
        TreeUtil.promiseSelectFirst(myTreeTable.getTree());
      }
    }
  }

  private void reloadModel() {
    try {
      myIsInRestore = true;
      ((DefaultTreeModel)myTreeTable.getTree().getModel()).reload();
    }
    finally {
      myIsInRestore = false;
    }

  }

  private void restoreTreeState() {

    try {
      myIsInRestore = true;
      getExpandedNodes(myProfile).restoreVisibleState(myTreeTable.getTree());
    }
    finally {
      myIsInRestore = false;
    }
  }

  private ActionToolbar createTreeToolbarPanel() {
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();

    DefaultActionGroup actions = new DefaultActionGroup();

    actions.add(new InspectionFilterAction(myProfile, myInspectionsFilter, getProject(), myProfileFilter));
    actions.addSeparator();

    actions.add(actionManager.createExpandAllAction(myTreeExpander, myTreeTable));
    actions.add(actionManager.createCollapseAllAction(myTreeExpander, myTreeTable));
    actions.add(new DumbAwareAction(
      InspectionsBundle.messagePointer("action.DumbAware.SingleInspectionProfilePanel.text.reset.to.empty"),
      InspectionsBundle.messagePointer("action.DumbAware.SingleInspectionProfilePanel.description.reset.to.empty"),
      AllIcons.Actions.Unselectall) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!isDisposed() && myProfile.isExecutable(getProject()));
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myProfile.resetToEmpty(getProject());
        loadDescriptorsConfigs(false);
        postProcessModification();
      }
    });
    if (myCreateInspectionActions != null) {
      actions.add(myCreateInspectionActions);
      actions.add(new CustomInspectionActions.RemoveInspectionAction(this));
    }
    for (InspectionProfileActionProvider provider : InspectionProfileActionProvider.EP_NAME.getExtensionList()) {
      for (AnAction action : provider.getActions(this)) {
        actions.add(action);
      }
    }

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("SingleInspectionProfile", actions, true);
    actionToolbar.setTargetComponent(this);
    return actionToolbar;
  }

  private void repaintTableData() {
    if (myTreeTable != null) {
      getExpandedNodes(myProfile).saveVisibleState(myTreeTable.getTree());
      reloadModel();
      restoreTreeState();
    }
  }

  public void selectInspectionTool(String name) {
    selectNode(findNodeByKey(name, myRoot));
  }

  public void selectInspectionGroup(String[] path) {
    final InspectionConfigTreeNode node = findGroupNodeByPath(path, 0, myRoot);
    selectNode(node);
    if (node != null) {
      myTreeTable.getTree().expandPath(new TreePath(node.getPath()));
    }
  }

  private void selectNode(InspectionConfigTreeNode node) {
    if (node != null) {
      TreeUtil.selectNode(myTreeTable.getTree(), node);
      final int rowForPath = myTreeTable.getTree().getRowForPath(new TreePath(node.getPath()));
      TableUtil.selectRows(myTreeTable, new int[]{rowForPath});
      scrollToCenter();
    }
  }

  private void scrollToCenter() {
    ListSelectionModel selectionModel = myTreeTable.getSelectionModel();
    int maxSelectionIndex = selectionModel.getMaxSelectionIndex();
    final int maxColumnSelectionIndex = Math.max(0, myTreeTable.getColumnModel().getSelectionModel().getMinSelectionIndex());
    Rectangle maxCellRect = myTreeTable.getCellRect(maxSelectionIndex, maxColumnSelectionIndex, false);

    final Point selectPoint = maxCellRect.getLocation();
    final int allHeight = myTreeTable.getVisibleRect().height;
    myTreeTable.scrollRectToVisible(new Rectangle(new Point(0, Math.max(0, selectPoint.y - allHeight / 2)), new Dimension(0, allHeight)));
  }

  private JScrollPane initTreeScrollPane() {
    fillTreeData(null, true);

    final InspectionsConfigTreeRenderer renderer = new InspectionsConfigTreeRenderer(){
      @Override
      protected String getFilter() {
        return myProfileFilter != null ? myProfileFilter.getFilter() : null;
      }
    };
    myTreeTable = InspectionsConfigTreeTable.create(new InspectionsConfigTreeTable.InspectionsConfigTreeTableSettings(myRoot, getProject()) {
      @Override
      protected void onChanged(final @NotNull InspectionConfigTreeNode node) {
        InspectionConfigTreeNode.updateUpHierarchy(node);
      }

      @Override
      public void updateRightPanel() {
        updateOptionsAndDescriptionPanel();
      }

      @Override
      public @NotNull InspectionProfileImpl getInspectionProfile() {
        return myProfile;
      }
    }, myDisposable);
    myTreeTable.setTreeCellRenderer(renderer);
    myTreeTable.setRootVisible(false);

    updateEmptyText();

    final TreeTableTree tree = myTreeTable.getTree();
    tree.putClientProperty(DefaultTreeUI.LARGE_MODEL_ALLOWED, true);
    tree.setRowHeight(renderer.getTreeCellRendererComponent(tree, "xxx", true, true, false, 0, true).getPreferredSize().height);
    tree.setLargeModel(true);

    tree.addTreeSelectionListener(__ -> {
      if (myTreeTable.getTree().getSelectionPaths() != null) {
        updateOptionsAndDescriptionPanel();
      }
      else {
        initOptionsAndDescriptionPanel();
      }

      if (!myIsInRestore) {
        if (!isDisposed()) {
          InspectionProfileImpl baseProfile = myProfile.getSource();
          getExpandedNodes(baseProfile).setSelectionPaths(myTreeTable.getTree().getSelectionPaths());
          getExpandedNodes(myProfile).setSelectionPaths(myTreeTable.getTree().getSelectionPaths());
        }
      }
    });

    myTreeTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final TreeTableTree tree = myTreeTable.getTree();
        final int[] selectionRows = tree.getSelectionRows();
        if (selectionRows != null &&
            tree.getPathForLocation(x, y) != null &&
            Arrays.binarySearch(selectionRows, tree.getRowForLocation(x, y)) > -1) {
          compoundPopup().show(comp, x, y);
        }
      }
    });

    TreeSpeedSearch.installOn(tree, false, o -> {
      final InspectionConfigTreeNode node = (InspectionConfigTreeNode)o.getLastPathComponent();
      return InspectionsConfigTreeComparator.getDisplayTextToSort(node.getText());
    });

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTreeTable);
    tree.setShowsRootHandles(true);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    TreeUtil.collapseAll(tree, 1);

    tree.addTreeExpansionListener(new TreeExpansionListener() {


      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        InspectionProfileModifiableModel selected = myProfile;
        getExpandedNodes(selected.getSource()).saveVisibleState(myTreeTable.getTree());
        getExpandedNodes(selected).saveVisibleState(myTreeTable.getTree());
      }

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        if (!isDisposed()) {
          final InspectionConfigTreeNode node = (InspectionConfigTreeNode)event.getPath().getLastPathComponent();
          getExpandedNodes(myProfile.getSource()).expandNode(node);
          getExpandedNodes(myProfile).expandNode(node);
        }
      }
    });

    myTreeExpander = new DefaultTreeTableExpander(myTreeTable);
    myProfileFilter = new MyFilterComponent();

    return scrollPane;
  }

  private JPopupMenu compoundPopup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final SeverityRegistrar severityRegistrar = myProfile.getProfileManager().getSeverityRegistrar();
    for (HighlightSeverity severity : LevelChooserAction.getSeverities(severityRegistrar, includeDoNotShow())) {
      final HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
      group.add(new AnAction(renderSeverity(severity), renderSeverity(severity), level.getIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          setNewHighlightingLevel(level);
        }

        @Override
        public boolean isDumbAware() {
          return true;
        }
      });
    }
    group.add(Separator.getInstance());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("InspectionProfilePanel", group);
    return menu.getComponent();
  }

  private boolean includeDoNotShow() {
    final TreePath[] paths = myTreeTable.getTree().getSelectionPaths();
    if (paths == null) return true;
    return includeDoNotShow(myTreeTable.getSelectedToolNodes());
  }

  private boolean includeDoNotShow(Collection<? extends InspectionConfigTreeNode.Tool> nodes) {
    final Project project = getProject();
    return !ContainerUtil.exists(nodes, node -> {
      final InspectionToolWrapper tool = myProfile.getToolDefaultState(node.getKey().getShortName(), project).getTool();
      return tool instanceof GlobalInspectionToolWrapper &&
             ((GlobalInspectionToolWrapper)tool).getSharedLocalInspectionToolWrapper() == null;
    });
  }

  private void fillTreeData(@Nullable String filter, boolean forceInclude) {
    if (isDisposed()) return;
    myRoot.removeAllChildren();
    myRoot.dropCache();
    List<Set<String>> keySetList = new ArrayList<>();
    final Set<String> quoted = new HashSet<>();
    if (filter != null && !filter.isEmpty()) {
      keySetList.addAll(SearchUtil.findKeys(filter, quoted));
    } else {
      // No filter -> add always visible groups
      myCustomGroups.forEach(group -> getGroupNode(myRoot, group.path()));
    }
    Project project = getProject();
    final boolean emptyFilter = myInspectionsFilter.isEmptyFilter();
    for (ToolDescriptors toolDescriptors : myInitialToolDescriptors.values()) {
      final Descriptor descriptor = toolDescriptors.getDefaultDescriptor();
      if (filter != null && !filter.isEmpty() && !isDescriptorAccepted(descriptor, filter, forceInclude, keySetList, quoted)) {
        continue;
      }
      final String shortName = toolDescriptors.getDefaultDescriptor().getShortName();
      final InspectionConfigTreeNode node = new InspectionConfigTreeNode.Tool(() -> myInitialToolDescriptors.get(shortName));
      if (!emptyFilter && !myInspectionsFilter.matches(myProfile.getTools(shortName, project), node)) {
        continue;
      }
      getGroupNode(myRoot, toolDescriptors.getDefaultDescriptor().getGroup()).add(node);
    }
    for (InspectionTreeAdvertiser.CustomGroup customGroup : myCustomGroups) {
      if (filter != null && isCustomGroupAccepted(customGroup, filter, quoted)) {
        getGroupNode(myRoot, customGroup.path());
      }
    }
    if (filter != null && forceInclude && myRoot.getChildCount() == 0) {
      final Set<String> filters = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
      if (filters.size() > 1 || !quoted.isEmpty()) {
        fillTreeData(filter, false);
      }
    }
    TreeUtil.sortRecursively(myRoot, InspectionsConfigTreeComparator.INSTANCE);
  }

  private void updateOptionsAndDescriptionPanel() {
    if (isDisposed()) {
      return;
    }
    Collection<InspectionConfigTreeNode.Tool> nodes = myTreeTable.getSelectedToolNodes();
    if (!nodes.isEmpty()) {
      final Project project = getProject();
      final InspectionConfigTreeNode.Tool singleNode = myTreeTable.getStrictlySelectedToolNode();
      final ScopeToolState toolState = singleNode != null ?
                                       myProfile.getToolDefaultState(singleNode.getDefaultDescriptor().getKey().getShortName(), project) : null;
      boolean showDefaultConfigurationOptions = toolState == null || toolState.getTool().getTool().showDefaultConfigurationOptions();
      if (singleNode != null) {
        // Inspection tool node selected
        final Descriptor descriptor = singleNode.getDefaultDescriptor();
        if (descriptor.loadDescription() != null) {
          // need this in order to correctly load plugin-supplied descriptions
          final Descriptor defaultDescriptor = singleNode.getDefaultDescriptor();
          final String description = defaultDescriptor.loadDescription(); //NON-NLS
          final InspectionProfileEntry tool = defaultDescriptor.getToolWrapper().getTool();
          try {
            if (description == null) throw new NullPointerException();
            @NlsSafe String markedDescription = SearchUtil.markup(SettingsUtil.wrapWithPoweredByMessage(description, tool.getClass().getClassLoader()), myProfileFilter.getFilter());
            DescriptionEditorPaneKt.readHTMLWithCodeHighlighting(myDescription,
                                                                 markedDescription,
                                                                 descriptor.getState().getTool().getLanguage());
          }
          catch (Throwable t) {
            LOG.error("Failed to load description for: " +
                      tool.getClass() +
                      "; description: " +
                      description, t);
          }
        }
        else {
          DescriptionEditorPaneKt.readHTML(myDescription, AnalysisBundle.message("inspections.settings.no.description.warning"));
        }
      }
      else {
        // Inspection group node selected
        showDescriptionForSelection();
      }

      myOptionsPanel.removeAll();
      JPanel severityPanel;
      final JPanel configPanelAnchor = new JPanel(new GridBagLayout());

      final boolean showOptionPanel;
      final double severityPanelWeightY;

      final Set<String> scopesNames = new HashSet<>();
      for (final InspectionConfigTreeNode.Tool node : nodes) {
        final List<ScopeToolState> nonDefaultTools = myProfile.getNonDefaultTools(node.getDefaultDescriptor().getKey().getShortName(), project);
        for (final ScopeToolState tool : nonDefaultTools) {
          scopesNames.add(tool.getScopeName());
        }
      }

      ScopesAndSeveritiesTable scopesAndScopesAndSeveritiesTable;
      myOptionsLabel = new JBLabel(AnalysisBundle.message("inspections.settings.options.title"));

      if (scopesNames.isEmpty()) {

        final HighlightSeverity severity = ScopesAndSeveritiesTable.getSeverity(
          ContainerUtil.map(nodes, node -> node.getDefaultDescriptor().getState())
        );

        // Scope
        final ScopesChooser scopesChooser = new ScopesChooser(ContainerUtil.map(nodes, node -> node.getDefaultDescriptor()), myProfile, project, null) {
          @Override
          protected void onScopesOrderChanged() {
            updateRecursively(nodes, true);
          }

          @Override
          protected void onScopeAdded(@NotNull String scopeName) {
            updateRecursively(nodes, true);
          }
        };

        final JComponent scopesChooserComponent = scopesChooser.createCustomComponent(
          scopesChooser.getTemplatePresentation(),
          ActionPlaces.UNKNOWN
        );

        // Highlighting
        final HighlightingChooser highlightingChooser = new HighlightingChooser(myProfile.getProfileManager().getSeverityRegistrar()) {
          @Override
          void onKeyChosen(@NotNull TextAttributesKey key) {
            final List<InspectionConfigTreeNode.Tool> toUpdate = new SmartList<>();
            for (final InspectionConfigTreeNode.Tool node : nodes) {
              final NamedScope scope = node.getDefaultDescriptor().getScope();
              final boolean doUpdate = myProfile.getEditorAttributesKey(node.getKey(), scope, project) != key;
              if (doUpdate) {
                myProfile.setEditorAttributesKey(node.getKey().getShortName(), key.getExternalName(), null, project);
                toUpdate.add(node);
              }
            }
            updateRecursively(toUpdate, true);
          }
        };
        final var highlightsChooserComponent = highlightingChooser.createCustomComponent(
          highlightingChooser.getTemplatePresentation(),
          ActionPlaces.UNKNOWN
        );
        updateHighlightingChooser(nodes, project, highlightingChooser);

        // Severity
        final LevelChooserAction severityLevelChooser =
          new LevelChooserAction(myProfile.getProfileManager().getSeverityRegistrar(),
                                 includeDoNotShow(nodes)) {
            @Override
            protected void onChosen(final @NotNull HighlightSeverity severity) {
              final HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
              final List<InspectionConfigTreeNode.Tool> toUpdate = new SmartList<>();
              for (final InspectionConfigTreeNode.Tool node : nodes) {
                final HighlightDisplayKey key = node.getDefaultDescriptor().getKey();
                final NamedScope scope = node.getDefaultDescriptor().getScope();
                final boolean doUpdate = myProfile.getErrorLevel(key, scope, project) != level
                                         || myProfile.getEditorAttributesKey(key, scope, project) != null;
                if (doUpdate) {
                  myProfile.setErrorLevel(key, level, null, project);
                  myProfile.setEditorAttributesKey(node.getKey().getShortName(), null, null, project);
                  updateHighlightingChooser(nodes, project, highlightingChooser);
                  toUpdate.add(node);
                }
              }
              updateRecursively(toUpdate, false);
            }
          };
        severityLevelChooser.setChosen(severity);

        final JComponent severityLevelChooserComponent = severityLevelChooser.createCustomComponent(
          severityLevelChooser.getTemplatePresentation(),
          ActionPlaces.UNKNOWN
        );
        severityLevelChooserComponent.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
          KeyStroke.getKeyStroke(KeyEvent.VK_V, MnemonicHelper.getFocusAcceleratorKeyMask()),
          "changeSeverity"
        );
        severityLevelChooserComponent.getActionMap().put("changeSeverity", new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            final var button = (ComboBoxAction.ComboBoxButton)severityLevelChooserComponent;
            button.showPopup();
          }
        });

        ScopesPanel scopesPanel = new ScopesPanel(scopesChooserComponent, severityLevelChooserComponent, highlightsChooserComponent);
        severityPanel = scopesPanel.panel;

        if (toolState != null) {
          setConfigPanel(configPanelAnchor, toolState);
          myOptionsLabel.setText(AnalysisBundle.message("inspections.settings.options.title"));
        }
        scopesAndScopesAndSeveritiesTable = null;

        showOptionPanel = configPanelAnchor.getComponentCount() != 0;
        severityPanelWeightY = 0;
      }
      else {
        if (singleNode != null) {
          for (final Descriptor descriptor : singleNode.getDescriptors().getNonDefaultDescriptors()) {
            descriptor.loadConfig();
          }
        }
        final var tableSettings = new ScopesAndSeveritiesTable.TableSettings(nodes, myProfile, project) {
          @Override
          protected void onScopeChosen(final @NotNull ScopeToolState state) {
            setConfigPanel(configPanelAnchor, state);
            configPanelAnchor.revalidate();
            configPanelAnchor.repaint();
          }

          @Override
          protected void onSettingsChanged() {
            updateRecursively(nodes, false);
          }

          @Override
          protected void onScopeAdded() {
            updateRecursively(nodes, false);
          }

          @Override
          protected void onScopesOrderChanged() {
            updateRecursively(nodes, true);
          }

          @Override
          protected void onScopeRemoved(final int scopesCount) {
            updateRecursively(nodes, scopesCount == 1);
          }
        };
        scopesAndScopesAndSeveritiesTable = new ScopesAndSeveritiesTable(tableSettings);

        final ToolbarDecorator wrappedTable = ToolbarDecorator
          .createDecorator(scopesAndScopesAndSeveritiesTable)
          .setToolbarPosition(ActionToolbarPosition.LEFT)
          .disableUpDownActions()
          .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
          .setRemoveActionUpdater(
            __ -> {
              final int selectedRow = scopesAndScopesAndSeveritiesTable.getSelectedRow();
              final int rowCount = scopesAndScopesAndSeveritiesTable.getRowCount();
              return rowCount - 1 != selectedRow;
            })
          .addExtraAction(new DumbAwareAction(IdeBundle.messagePointer("action.Anonymous.text.edit.scopes.order"), AllIcons.General.GearPlain) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              final ScopesOrderDialog dlg = new ScopesOrderDialog(scopesAndScopesAndSeveritiesTable, myProfile, project);
              if (dlg.showAndGet()) {
                tableSettings.onScopesOrderChanged();
              }
            }
          });
        severityPanel = wrappedTable.createPanel();
        final Dimension panelSize = new Dimension(getMinimumSize().width, 81);
        severityPanel.setMinimumSize(panelSize);
        severityPanel.setPreferredSize(panelSize);
        severityPanel.setMaximumSize(panelSize);
        showOptionPanel = configPanelAnchor.getComponentCount() != 0;
        severityPanelWeightY = showOptionPanel ? 0 : 1;
      }

      final GridBag constraint = new GridBag()
        .setDefaultWeightX(1.0)
        .setDefaultWeightY(1.0);
      if (showDefaultConfigurationOptions) {
        myOptionsPanel.add(severityPanel, constraint.nextLine().weighty(severityPanelWeightY).fillCell().insetTop(SECTION_GAP));
      }

      GuiUtils.enableChildren(myOptionsPanel, isThoughOneNodeEnabled(nodes));
      if (showOptionPanel) {
        if (showDefaultConfigurationOptions) {
          myOptionsSeparator = new ToolOptionsSeparator(scopesAndScopesAndSeveritiesTable);
          myOptionsPanel.add(myOptionsSeparator, constraint.nextLine().weighty(0).fillCellHorizontally());
        }
        myOptionsPanel.add(configPanelAnchor, constraint.nextLine().fillCell());
      }
      else if (scopesNames.isEmpty()) {
        myOptionsPanel.add(configPanelAnchor, constraint.nextLine().fillCell());
      }
      myOptionsPanel.revalidate();
    }
    else {
      initOptionsAndDescriptionPanel();
      final TreePath[] selectedPaths = myTreeTable.getTree().getSelectionPaths();
      if (selectedPaths != null && selectedPaths.length > 0) {
        showDescriptionForSelection();
      }
    }
    myOptionsPanel.repaint();
  }

  private void showDescriptionForSelection() {
    var group = myTreeTable.getStrictlySelectedGroupNode();
    InspectionTreeAdvertiser.CustomGroup customGroup = null;
    if (group != null) {
      customGroup = ContainerUtil.find(
        myCustomGroups,
        g -> Arrays.equals(
          g.path(),
          Arrays.stream(group.getPath()).skip(1).map(node -> ((InspectionConfigTreeNode)node).getText()).toArray()
        )
      );
    }
    if (customGroup != null) {
      DescriptionEditorPaneKt.readHTML(myDescription, SearchUtil.markup(customGroup.description(), myProfileFilter.getFilter()));
    } else {
      DescriptionEditorPaneKt.readHTML(myDescription, AnalysisBundle.message("inspections.settings.multiple.inspections.warning"));
    }
  }

  private static void updateHighlightingChooser(Collection<? extends InspectionConfigTreeNode.Tool> nodes,
                                                Project project,
                                                HighlightingChooser highlightingChooser) {
    final TextAttributesKey key = ScopesAndSeveritiesTable.getEditorAttributesKey(
      ContainerUtil.map(nodes, node -> node.getDefaultDescriptor().getState()),
      project
    );
    highlightingChooser.setChosen(key);
  }

  private void updateRecursively(Collection<? extends InspectionConfigTreeNode> nodes, boolean updateOptionsAndDescriptionPanel) {
    InspectionConfigTreeNode.updateUpHierarchy(nodes);
    myTreeTable.repaint();
    if (updateOptionsAndDescriptionPanel) {
      updateOptionsAndDescriptionPanel();
    }
  }

  private boolean isThoughOneNodeEnabled(Collection<? extends InspectionConfigTreeNode.Tool> nodes) {
    final Project project = getProject();
    for (final InspectionConfigTreeNode.Tool node : nodes) {
      final String toolId = node.getKey().getShortName();
      if (myProfile.getTools(toolId, project).isEnabled()) {
        return true;
      }
    }
    return false;
  }

  private void initOptionsAndDescriptionPanel() {
    myOptionsPanel.removeAll();
    DescriptionEditorPaneKt.readHTML(myDescription, DescriptionEditorPane.EMPTY_HTML);
    myOptionsPanel.validate();
    myOptionsPanel.repaint();
  }

  public @NotNull InspectionProfileModifiableModel getProfile() {
    return myProfile;
  }

  public InspectionToolWrapper<?, ?> getSelectedTool() {
    InspectionConfigTreeNode.Tool node = myTreeTable.getStrictlySelectedToolNode();
    if (node == null) return null;
    return node.getDefaultDescriptor().getToolWrapper();
  }

  public void removeSelectedRow() {
    final InspectionConfigTreeNode.Tool node = myTreeTable.getStrictlySelectedToolNode();
    if (node != null) {
      getExpandedNodes(myProfile).saveVisibleState(myTreeTable.getTree());
      final TreePath path = myTreeTable.getTree().getSelectionPath();
      assert path != null;
      final TreePath newPath = path.getParentPath().pathByAddingChild(node.getPreviousNode());
      myTreeTable.removeSelectedPath(path);
      myTreeTable.addSelectedPath(newPath);
      restoreTreeState();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(700, 500);
  }

  public void disposeUI() {
    myAlarm.cancelAllRequests();
    if (myProfileFilter != null) {
      myProfileFilter.dispose();
    }
    if (myInspectionProfilePanel != null) {
      for (ScopeToolState state : myProfile.getAllTools()) {
        state.resetConfigPanel();
      }
    }
    myProfile.cleanup(myProjectProfileManager.getProject());
    Disposer.dispose(myDisposable);
    myDisposable = null;
  }

  public static HyperlinkAdapter createSettingsHyperlinkListener(Project project) {
    return createSettingsHyperlinkListener(project, null);
  }

  public static HyperlinkAdapter createSettingsHyperlinkListener(Project project, SingleInspectionProfilePanel panel) {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        try {
          URI url = new URI(e.getDescription());
          if (url.getScheme().equals("settings")) {
            DataContext context = DataManager.getInstance().getDataContextFromFocus().getResult();
            if (context != null) {
              Settings settings = Settings.KEY.getData(context);
              SearchTextField searchTextField = SearchTextField.KEY.getData(context);
              String configId = url.getAuthority(); //using `getAuthority` instead of `getHost` to support white spaces in configId
              String search = url.getQuery();
              if (settings != null) {
                Configurable configurable = settings.find(configId);
                settings.select(configurable).doWhenDone(() -> {
                  if (searchTextField != null && search != null) searchTextField.setText(search);
                });
              } else {
                ShowSettingsUtilImpl.showSettingsDialog(project, configId, search);
              }
            }
          }
          else if (url.getScheme().equals("action") && panel != null) {
            final var action = InspectionProfileActionProvider.EP_NAME.getExtensionList().stream()
              .flatMap(provider -> provider.getProfilePanelActions(panel).stream())
              .filter(a -> a.actionId().equals(url.getAuthority()))
              .findFirst();
            if (action.isPresent()) {
              final AnAction urlAction = action.get().action();
              if (urlAction instanceof ActionGroup group) {
                final ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, group);
                final Point point = new Point();
                if (e.getInputEvent() instanceof MouseEvent mouseEvent) {
                  point.x = mouseEvent.getX();
                  point.y = mouseEvent.getY();
                }
                menu.getComponent().show((Component)e.getSource(), point.x, point.y);
              } else {
                final AnActionEvent event = AnActionEvent.createFromInputEvent(
                  e.getInputEvent(), ActionPlaces.UNKNOWN, null, DataContext.EMPTY_CONTEXT
                );
                action.get().action().actionPerformed(event);
              }
            }
          }
          else {
            BrowserUtil.browse(url);
          }
        }
        catch (URISyntaxException ex) {
          LOG.error(ex);
        }
      }
    };
  }

  private JPanel createInspectionProfileSettingsPanel() {

    myDescription = new DescriptionEditorPane();
    myDescription.addHyperlinkListener(createSettingsHyperlinkListener(getProject(), this));

    initToolStates();
    fillTreeData(myProfileFilter != null ? myProfileFilter.getFilter() : null, true);

    OnePixelSplitter rightSplitter = new OnePixelSplitter(true,
                                                    "SingleInspectionProfilePanel.HORIZONTAL_DIVIDER_PROPORTION",
                                                    DIVIDER_PROPORTION_DEFAULT);

    JBScrollPane descriptionPanel = new JBScrollPane(myDescription);
    descriptionPanel.setBorder(JBUI.Borders.empty());
    rightSplitter.setFirstComponent(descriptionPanel);

    myOptionsPanel = new JPanel(new GridBagLayout());
    initOptionsAndDescriptionPanel();
    rightSplitter.setSecondComponent(myOptionsPanel);

    final JScrollPane tree = initTreeScrollPane();

    final JPanel northPanel = new JPanel(new GridBagLayout());
    northPanel.setBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0));
    myProfileFilter.setPreferredSize(new Dimension(20, myProfileFilter.getPreferredSize().height));
    final GridBag constraint = new GridBag()
      .setDefaultWeightY(1.0);
    northPanel.add(myProfileFilter, constraint.next().weightx(0.5).fillCellHorizontally().anchor(GridBagConstraints.BASELINE_TRAILING));
    northPanel.add(createTreeToolbarPanel().getComponent(), constraint.next().weightx(1.0).anchor(GridBagConstraints.WEST));

    JBSplitter mainSplitter = new JBSplitter(false, DIVIDER_PROPORTION_DEFAULT, 0.35f, 0.65f);
    mainSplitter.setSplitterProportionKey("SingleInspectionProfilePanel.VERTICAL_DIVIDER_PROPORTION");
    mainSplitter.setFirstComponent(tree);
    mainSplitter.setSecondComponent(rightSplitter);
    mainSplitter.setDividerWidth(SECTION_GAP);

    final JPanel inspectionTreePanel = new JPanel(new BorderLayout());
    inspectionTreePanel.add(northPanel, BorderLayout.NORTH);
    inspectionTreePanel.add(mainSplitter, BorderLayout.CENTER);

    final JBCheckBox disableNewInspectionsCheckBox = new JBCheckBox(
      AnalysisBundle.message("inspections.settings.disable.new.inspections.by.default.checkbox"),
      getProfile().isProfileLocked());

    JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    panel.add(inspectionTreePanel, BorderLayout.CENTER);
    panel.add(disableNewInspectionsCheckBox, BorderLayout.SOUTH);
    disableNewInspectionsCheckBox.addItemListener(__ -> {
      final boolean enabled = disableNewInspectionsCheckBox.isSelected();
      if (!isDisposed()) {
        final InspectionProfileImpl profile = getProfile();
        profile.lockProfile(enabled);
      }
    });

    return panel;
  }

  public boolean isModified() {
    if (myTreeTable == null) return false;
    if (myModified) return true;
    if (myProfile.isChanged()) return true;
    if (myProfile.getSource().isProjectLevel() != myProfile.isProjectLevel()) return true;
    if (!Comparing.strEqual(myProfile.getSource().getName(), myProfile.getName())) return true;
    if (!myInitialScopesOrder.equals(myProfile.getScopesOrder())) return true;
    return descriptorsAreChanged();
  }

  public void reset() {
    myModified = false;
    filterTree();
    final String filter = myProfileFilter.getFilter();
    myProfileFilter.reset();
    myProfileFilter.setSelectedItem(filter);
    myProfile.setName(myProfile.getSource().getName());
    myProfile.setProjectLevel(myProfile.getSource().isProjectLevel());
  }

  public void apply() {
    final boolean modified = isModified();
    if (!modified) {
      return;
    }
    InspectionProfileModifiableModel selectedProfile = myProfile;

    BaseInspectionProfileManager profileManager = selectedProfile.isProjectLevel() ? myProjectProfileManager : (BaseInspectionProfileManager)InspectionProfileManager.getInstance();
    InspectionProfileImpl source = selectedProfile.getSource();

    if (source.getProfileManager() != profileManager) {
      source.getProfileManager().deleteProfile(source);
    }

    if (selectedProfile.getProfileManager() != profileManager) {
      copyUsedSeveritiesIfUndefined(selectedProfile, profileManager);
      selectedProfile.setProfileManager(profileManager);
    }

    selectedProfile.commit();
    profileManager.addProfile(source);
    profileManager.fireProfileChanged(source);

    myModified = false;
    myRoot.dropCache();
    initToolStates();
    updateOptionsAndDescriptionPanel();
  }

  private boolean descriptorsAreChanged() {
    return ContainerUtil.exists(myInitialToolDescriptors.values(),
                  toolDescriptors -> areToolDescriptorsChanged(getProject(), myProfile, toolDescriptors));
  }

  public static boolean areToolDescriptorsChanged(@NotNull Project project,
                                                  @NotNull InspectionProfileModifiableModel profile,
                                                  @NotNull ToolDescriptors toolDescriptors) {
    Descriptor desc = toolDescriptors.getDefaultDescriptor();
    if (profile.isToolEnabled(desc.getKey(), null, project) != desc.isEnabled()) {
      return true;
    }
    if (profile.getErrorLevel(desc.getKey(), desc.getScope(), project) != desc.getLevel()) {
      return true;
    }
    if (!Objects.equals(profile.getEditorAttributesKey(desc.getKey(), desc.getScope(), project),
                        desc.getEditorAttributesKey())) {
      return true;
    }
    final List<Descriptor> descriptors = toolDescriptors.getNonDefaultDescriptors();
    for (Descriptor descriptor : descriptors) {
      if (descriptor.getScope() == null) {
        // Missing scope -> profile will return default descriptor info instead
        continue;
      }
      if (profile.isToolEnabled(descriptor.getKey(), descriptor.getScope(), project) != descriptor.isEnabled()) {
        return true;
      }
      if (profile.getErrorLevel(descriptor.getKey(), descriptor.getScope(), project) != descriptor.getLevel()) {
        return true;
      }
      if (!Objects.equals(profile.getEditorAttributesKey(descriptor.getKey(), descriptor.getScope(), project),
                          descriptor.getEditorAttributesKey())) {
        return true;
      }
    }

    final List<ScopeToolState> tools = profile.getNonDefaultTools(desc.getKey().getShortName(), project);
    if (tools.size() != descriptors.size()) {
      return true;
    }
    for (int i = 0; i < tools.size(); i++) {
      final ScopeToolState pair = tools.get(i);
      if (!Comparing.equal(pair.getScope(project), descriptors.get(i).getScope())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setVisible(boolean aFlag) {
    if (aFlag && myInspectionProfilePanel == null) {
      // to ensure that profile initialized with proper project
      myProfile.initInspectionTools(myProjectProfileManager.getProject());

      initUI();
    }
    super.setVisible(aFlag);
  }

  private void setNewHighlightingLevel(@NotNull HighlightDisplayLevel level) {
    Collection<InspectionConfigTreeNode.Tool> tools = myTreeTable.getSelectedToolNodes();
    if (!tools.isEmpty()) {
      for (InspectionConfigTreeNode.Tool tool : tools) {
        updateErrorLevel(tool, level);
      }
      updateOptionsAndDescriptionPanel();
    } else {
      initOptionsAndDescriptionPanel();
    }
    repaintTableData();
  }

  private void updateErrorLevel(final InspectionConfigTreeNode.Tool child, @NotNull HighlightDisplayLevel level) {
    final HighlightDisplayKey key = child.getKey();
    myProfile.setErrorLevel(key, level, null, getProject());
    child.dropCache();
  }

  public JComponent getPreferredFocusedComponent() {
    return myTreeTable;
  }

  private void updateEmptyText() {
    final var emptyText = myTreeTable.getEmptyText();
    emptyText.setText(AnalysisBundle.message("inspections.settings.empty.text"));
    if (!myInspectionsFilter.isEmptyFilter()) {
      emptyText.appendLine(
        AnalysisBundle.message("inspections.settings.empty.text.filters.link"),
        SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        e -> { myInspectionsFilter.reset(); }
      );
    } else if (myCreateInspectionActions.getChildrenCount() > 0) {
      emptyText
        .appendSecondaryText(
          AnalysisBundle.message("inspections.settings.empty.text.inspection.link"),
          SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
          e -> JBPopupFactory
            .getInstance()
            .createActionGroupPopup(
              null,
              myCreateInspectionActions,
              DataManager.getInstance().getDataContext(myInspectionProfilePanel),
              null,
              true)
            .show(new RelativePoint(myTreeTable, myTreeTable.getEmptyText().getPointBelow()))
        );
    }
  }

  private final class MyFilterComponent extends FilterComponent {
    private MyFilterComponent() {
      super(INSPECTION_FILTER_HISTORY, 10);
    }

    @Override
    public void filter() {
      filterTree();
    }

    @Override
    protected void onlineFilter() {
      if (isDisposed()) return;
      final String filter = getFilter();
      getExpandedNodes(myProfile).saveVisibleState(myTreeTable.getTree());
      fillTreeData(filter, true);
      reloadModel();
      if (filter == null || filter.isEmpty()) {
        restoreTreeState();
      } else {
        TreeUtil.expandAll(myTreeTable.getTree());
      }
    }
  }

  private final class ToolOptionsSeparator extends JPanel {
    private final ActionLink myResetLink;
    private final @Nullable ScopesAndSeveritiesTable myScopesAndSeveritiesTable;

    ToolOptionsSeparator(@Nullable ScopesAndSeveritiesTable scopesAndSeveritiesTable) {
      myScopesAndSeveritiesTable = scopesAndSeveritiesTable;
      setLayout(new GridBagLayout());
      setBorder(JBUI.Borders.emptyTop(IdeBorderFactory.TITLED_BORDER_INDENT));
      final GridBag constraint = new GridBag()
        .setDefaultWeightY(1.0);
      add(myOptionsLabel, constraint.next().anchor(GridBagConstraints.LINE_START));
      add(new JSeparator(SwingConstants.HORIZONTAL), constraint.next().weightx(1.0).anchor(GridBagConstraints.CENTER).fillCellHorizontally().insets(2, TitledSeparator.SEPARATOR_LEFT_INSET, 0, TitledSeparator.SEPARATOR_RIGHT_INSET));

      myResetLink = new ActionLink(IdeBundle.message("reset.action.text"), e -> {
        ScopeToolState state = getSelectedState();
        if (state != null) {
          state.resetConfigPanel();
          Project project = getProject();
          myProfile.resetToBase(state.getTool().getTool().getShortName(), state.getScope(project), project);
          updateOptionsAndDescriptionPanel();
        }
      });
      add(myResetLink, constraint.next().anchor(GridBagConstraints.LINE_END).insetLeft(6));
      setupResetLinkVisibility();
    }

    private void setupResetLinkVisibility() {
      if (myTreeTable == null || isDisposed()) return;
      InspectionConfigTreeNode.Tool node = myTreeTable.getStrictlySelectedToolNode();
      if (node != null) {
        ScopeToolState state = getSelectedState();
        if (state == null) return;
        Project project = getProject();
        NamedScope scope = state.getScope(project);
        if (scope == null) return;
        boolean canReset = !myProfile.isProperSetting(state.getTool().getTool().getShortName(), scope, project);

        myResetLink.setVisible(canReset);
        revalidate();
        repaint();
      }
    }

    private ScopeToolState getSelectedState() {
      InspectionConfigTreeNode.Tool node = myTreeTable.getStrictlySelectedToolNode();
      if (node == null) return null;
      if (myScopesAndSeveritiesTable != null) {
        List<ScopeToolState> selectedStates = myScopesAndSeveritiesTable.getSelectedStates();
        LOG.assertTrue(selectedStates.size() == 1);
        return selectedStates.get(0);
      } else {
        return node.getDescriptors().getDefaultDescriptor().getState();
      }
    }
  }
}
