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

package com.intellij.profile.codeInspection.ui;

import com.intellij.CommonBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.SeverityUtil;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManagerImpl;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.profile.codeInspection.ui.actions.AddScopeAction;
import com.intellij.profile.codeInspection.ui.actions.DeleteScopeAction;
import com.intellij.profile.codeInspection.ui.actions.MoveScopeAction;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.IconUtil;
import com.intellij.util.config.StorageAccessors;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 31-May-2006
 */
public class SingleInspectionProfilePanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolsPanel");
  @NonNls private static final String INSPECTION_FILTER_HISTORY = "INSPECTION_FILTER_HISTORY";
  private static final String UNDER_CONSTRUCTION = InspectionsBundle.message("inspection.tool.description.under.construction.text");
  private final Map<Descriptor, List<Descriptor>> myDescriptors = new HashMap<Descriptor, List<Descriptor>>();
  private InspectionProfileImpl mySelectedProfile;
  private JEditorPane myBrowser;
  private JPanel myOptionsPanel;
  private JPanel myInspectionProfilePanel = null;
  private FilterComponent myProfileFilter;
  private final InspectionConfigTreeNode myRoot =
    new InspectionConfigTreeNode(InspectionsBundle.message("inspection.root.node.title"), null, false, false);
  private final Alarm myAlarm = new Alarm();
  private boolean myModified = false;
  private Tree myTree;
  private TreeExpander myTreeExpander;
  @NotNull
  private String myInitialProfile;
  @NonNls private static final String EMPTY_HTML = "<html><body></body></html>";
  private boolean myIsInRestore = false;
  @NonNls private static final String VERTICAL_DIVIDER_PROPORTION = "VERTICAL_DIVIDER_PROPORTION";
  @NonNls private static final String HORIZONTAL_DIVIDER_PROPORTION = "HORIZONTAL_DIVIDER_PROPORTION";
  private final StorageAccessors myProperties = StorageAccessors.createGlobal("SingleInspectionProfilePanel");

  private boolean myShareProfile;
  private final InspectionProjectProfileManager myProjectProfileManager;
  private Splitter myRightSplitter;
  private Splitter myMainSplitter;

  public SingleInspectionProfilePanel(@NotNull InspectionProjectProfileManager projectProfileManager,
                                      @NotNull String inspectionProfileName,
                                      @NotNull ModifiableModel profile) {
    super(new BorderLayout());
    myProjectProfileManager = projectProfileManager;
    mySelectedProfile = (InspectionProfileImpl)profile;
    myInitialProfile = inspectionProfileName;
    myShareProfile = profile.getProfileManager() == projectProfileManager;
  }

  private static VisibleTreeState getExpandedNodes(InspectionProfileImpl profile) {
    if (profile.getProfileManager() instanceof ApplicationProfileManager) {
      return AppInspectionProfilesVisibleTreeState.getInstance().getVisibleTreeState(profile);
    }
    else {
      DefaultProjectProfileManager projectProfileManager = (DefaultProjectProfileManager)profile.getProfileManager();
      return ProjectInspectionProfilesVisibleTreeState.getInstance(projectProfileManager.getProject()).getVisibleTreeState(profile);
    }
  }

  private void initUI() {
    myInspectionProfilePanel = createInspectionProfileSettingsPanel();
    add(myInspectionProfilePanel, BorderLayout.CENTER);
    UserActivityWatcher userActivityWatcher = new UserActivityWatcher();
    userActivityWatcher.addUserActivityListener(new UserActivityListener() {
      @Override
      public void stateChanged() {
        //invoke after all other listeners
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (mySelectedProfile == null) return; //panel was disposed
            updateProperSettingsForSelection();
            wereToolSettingsModified();
          }
        });
      }
    });
    userActivityWatcher.register(myOptionsPanel);
    updateSelectedProfileState();
    reset();
  }

  private void updateSelectedProfileState() {
    if (mySelectedProfile == null) return;
    restoreTreeState();
    repaintTableData();
    updateSelection();
  }

  public void updateSelection() {
    if (myTree != null) {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        TreeUtil.selectNode(myTree, (TreeNode)selectionPath.getLastPathComponent());
        TreeUtil.showRowCentered(myTree, myTree.getRowForPath(selectionPath), false);
      }
    }
  }


  private void wereToolSettingsModified() {
    for (Map.Entry<Descriptor, List<Descriptor>> entry : myDescriptors.entrySet()) {
      Descriptor desc = entry.getKey();
      if (wereToolSettingsModified(desc)) return;
      List<Descriptor> descriptors = entry.getValue();
      for (Descriptor descriptor : descriptors) {
        if (wereToolSettingsModified(descriptor)) return;
      }
    }
    myModified = false;
  }

  private boolean wereToolSettingsModified(Descriptor descriptor) {
    InspectionToolWrapper toolWrapper = descriptor.getToolWrapper();
    if (!mySelectedProfile.isToolEnabled(descriptor.getKey(), descriptor.getScope(), myProjectProfileManager.getProject())) {
      return false;
    }
    Element oldConfig = descriptor.getConfig();
    if (oldConfig == null) return false;
    Element newConfig = Descriptor.createConfigElement(toolWrapper);
    if (!JDOMUtil.areElementsEqual(oldConfig, newConfig)) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myTree.repaint();
        }
      }, 300);
      myModified = true;
      return true;
    }
    return false;
  }

  private void updateProperSettingsForSelection() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      InspectionConfigTreeNode node = (InspectionConfigTreeNode)selectionPath.getLastPathComponent();
      final Descriptor descriptor = node.getDescriptor();
      if (descriptor != null) {
        final boolean properSetting = mySelectedProfile.isProperSetting(descriptor.getKey().toString());
        if (node.isProperSetting() != properSetting) {
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(new Runnable() {
            @Override
            public void run() {
              myTree.repaint();
            }
          }, 300);
          node.dropCache();
          updateUpHierarchy(node, (InspectionConfigTreeNode)node.getParent());
        }
      }
    }
  }

  private void initDescriptors() {
    final InspectionProfileImpl profile = mySelectedProfile;
    if (profile == null) return;
    myDescriptors.clear();
    List<ScopeToolState> tools = profile.getDefaultStates(myProjectProfileManager.getProject());
    for (ScopeToolState state : tools) {
      final ArrayList<Descriptor> descriptors = new ArrayList<Descriptor>();
      if (state.getLevel() == HighlightDisplayLevel.NON_SWITCHABLE_ERROR) {
        continue;
      }
      Project project = myProjectProfileManager.getProject();
      myDescriptors.put(new Descriptor(state, profile, project), descriptors);
      InspectionToolWrapper toolWrapper = state.getTool();
      final List<ScopeToolState> nonDefaultTools = profile.getNonDefaultTools(toolWrapper.getShortName(), project);
      for (ScopeToolState nonDefaultToolState : nonDefaultTools) {
        descriptors.add(new Descriptor(nonDefaultToolState, profile, project));
      }
    }
  }

  private void postProcessModification() {
    wereToolSettingsModified();
    //resetup configs
    for (ScopeToolState state : mySelectedProfile.getAllTools(myProjectProfileManager.getProject())) {
      state.resetConfigPanel();
    }
    fillTreeData(myProfileFilter.getFilter(), true);
    repaintTableData();
    updateOptionsAndDescriptionPanel(myTree.getSelectionPath());
  }

  @Nullable
  public static ModifiableModel createNewProfile(final int initValue,
                                                 ModifiableModel selectedProfile,
                                                 JPanel parent,
                                                 String profileName,
                                                 Set<String> existingProfileNames,
                                                 @NotNull Project project) {
    profileName = Messages.showInputDialog(parent, profileName, "Create New Inspection Profile", Messages.getQuestionIcon());
    if (profileName == null) return null;
    final ProfileManager profileManager = selectedProfile.getProfileManager();
    if (existingProfileNames.contains(profileName)) {
      Messages.showErrorDialog(InspectionsBundle.message("inspection.unable.to.create.profile.message", profileName),
                               InspectionsBundle.message("inspection.unable.to.create.profile.dialog.title"));
      return null;
    }
    InspectionProfileImpl inspectionProfile =
        new InspectionProfileImpl(profileName, InspectionToolRegistrar.getInstance(), profileManager);
      if (initValue == -1) {
        inspectionProfile.initInspectionTools(project);
        ModifiableModel profileModifiableModel = inspectionProfile.getModifiableModel();
        final InspectionToolWrapper[] profileEntries = profileModifiableModel.getInspectionTools(null);
        for (InspectionToolWrapper toolWrapper : profileEntries) {
          profileModifiableModel.disableTool(toolWrapper.getShortName(), (NamedScope)null, project);
        }
        profileModifiableModel.setLocal(true);
        profileModifiableModel.setModified(true);
        return profileModifiableModel;
      } else if (initValue == 0) {
        inspectionProfile.copyFrom(selectedProfile);
        inspectionProfile.setName(profileName);
        inspectionProfile.initInspectionTools(project);
        inspectionProfile.setModified(true);
        return inspectionProfile;
      }
      return null;
  }

  public void setFilter(String filter) {
    myProfileFilter.setFilter(filter);
  }

  public void filterTree(String filter) {
    if (myTree != null) {
      getExpandedNodes(mySelectedProfile).saveVisibleState(myTree);
      fillTreeData(filter, true);
      reloadModel();
      restoreTreeState();
      if (myTree.getSelectionPath() == null) {
        TreeUtil.selectFirstNode(myTree);
      }
    }
  }

  private void reloadModel() {
    try {
      myIsInRestore = true;
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
    finally {
      myIsInRestore = false;
    }

  }

  private void restoreTreeState() {

    try {
      myIsInRestore = true;
      getExpandedNodes(mySelectedProfile).restoreVisibleState(myTree);
    }
    finally {
      myIsInRestore = false;
    }
  }

  private ActionToolbar createTreeToolbarPanel() {
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(actionManager.createExpandAllAction(myTreeExpander, myTree));
    actions.add(actionManager.createCollapseAllAction(myTreeExpander, myTree));

    actions.add(new AnAction(CommonBundle.message("button.reset.to.default"), CommonBundle.message("button.reset.to.default"),
                             AllIcons.General.Reset) {
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_MASK)), myTree);
      }
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myRoot.isProperSetting());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        mySelectedProfile.resetToBase(myProjectProfileManager.getProject());
        postProcessModification();
      }
    });

    actions.add(new AnAction("Reset to Empty", "Reset to empty", AllIcons.Actions.Reset_to_empty){

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(mySelectedProfile != null && mySelectedProfile.isExecutable(myProjectProfileManager.getProject()));
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        mySelectedProfile.resetToEmpty(e.getProject());
        postProcessModification();
      }
    });

    actions.add(new ToggleAction("Lock Profile", "Lock profile", AllIcons.Nodes.Padlock) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return mySelectedProfile != null && mySelectedProfile.isProfileLocked();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        mySelectedProfile.lockProfile(state);
      }
    });

    actions.addSeparator();
    actions.add(new MyAddScopeAction());
    actions.add(new MyDeleteScopeAction());
    actions.add(new MoveScopeAction(myTree, "Move Scope Up", IconUtil.getMoveUpIcon(), -1) {
      @Override
      protected boolean isEnabledFor(int idx, InspectionConfigTreeNode parent) {
        return idx > 0;
      }

      @Override
      protected InspectionProfileImpl getSelectedProfile() {
        return mySelectedProfile;
      }
    });
    actions.add(new MoveScopeAction(myTree, "Move Scope Down", IconUtil.getMoveDownIcon(), 1) {
      @Override
      protected boolean isEnabledFor(int idx, InspectionConfigTreeNode parent) {
        return idx < parent.getChildCount() - 2;
      }

      @Override
      protected InspectionProfileImpl getSelectedProfile() {
        return mySelectedProfile;
      }
    });
    actions.addSeparator();

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
    actionToolbar.setTargetComponent(this);
    return actionToolbar;
  }

  private void repaintTableData() {
    if (myTree != null) {
      getExpandedNodes(mySelectedProfile).saveVisibleState(myTree);
      reloadModel();
      restoreTreeState();
    }
  }

  public void selectInspectionTool(String name) {
    final InspectionConfigTreeNode node = findNodeByKey(name, myRoot);
    if (node != null) {
      TreeUtil.showRowCentered(myTree, myTree.getRowForPath(new TreePath(node.getPath())) - 1, true);//myTree.isRootVisible ? 0 : 1;
      TreeUtil.selectNode(myTree, node);
    }
  }

  @Nullable
  private static InspectionConfigTreeNode findNodeByKey(String name, InspectionConfigTreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      final InspectionConfigTreeNode child = (InspectionConfigTreeNode)root.getChildAt(i);
      final Descriptor descriptor = child.getDescriptor();
      if (descriptor != null) {
        if (descriptor.getKey().toString().equals(name)) {
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

  private JScrollPane initTreeScrollPane() {

    fillTreeData(null, true);

    final InspectionsConfigTreeRenderer renderer = new InspectionsConfigTreeRenderer(myProjectProfileManager.getProject()){
      @Override
      protected String getFilter() {
        return myProfileFilter != null ? myProfileFilter.getFilter() : null;
      }
    };
    myTree = new CheckboxTree(renderer, myRoot) {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size = new Dimension(size.width + 10, size.height);
        return size;
      }

      @Override
      protected void onNodeStateChanged(final CheckedTreeNode node) {
        toggleToolNode((InspectionConfigTreeNode)node);
      }
    };


    myTree.setCellRenderer(renderer);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);
    TreeUtil.installActions(myTree);


    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myTree.getSelectionPaths() != null && myTree.getSelectionPaths().length == 1) {
          updateOptionsAndDescriptionPanel(myTree.getSelectionPaths()[0]);
        }
        else {
          initOptionsAndDescriptionPanel();
        }

        if (!myIsInRestore) {
          InspectionProfileImpl selected = mySelectedProfile;
          if (selected != null) {
            InspectionProfileImpl baseProfile = (InspectionProfileImpl)selected.getParentProfile();
            if (baseProfile != null) {
              getExpandedNodes(baseProfile).setSelectionPaths(myTree.getSelectionPaths());
            }
            getExpandedNodes(selected).setSelectionPaths(myTree.getSelectionPaths());
          }
        }

      }
    });


    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final int[] selectionRows = myTree.getSelectionRows();
        if (selectionRows != null && myTree.getPathForLocation(x, y) != null && Arrays.binarySearch(selectionRows, myTree.getRowForLocation(x, y)) > -1)
        {
          compoundPopup().show(comp, x, y);
        }
      }
    });


    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath o) {
        final InspectionConfigTreeNode node = (InspectionConfigTreeNode)o.getLastPathComponent();
        final Descriptor descriptor = node.getDescriptor();
        return descriptor != null ? InspectionsConfigTreeComparator.getDisplayTextToSort(descriptor.getText()) : InspectionsConfigTreeComparator
          .getDisplayTextToSort(node.getGroupName());
      }
    });


    myTree.setSelectionModel(new DefaultTreeSelectionModel());

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    TreeUtil.collapseAll(myTree, 1);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {


      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        InspectionProfileImpl selected = mySelectedProfile;
        final InspectionConfigTreeNode node = (InspectionConfigTreeNode)event.getPath().getLastPathComponent();
        final InspectionProfileImpl parentProfile = (InspectionProfileImpl)selected.getParentProfile();
        if (parentProfile != null) {
          getExpandedNodes(parentProfile).saveVisibleState(myTree);
        }
        getExpandedNodes(selected).saveVisibleState(myTree);
      }

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        InspectionProfileImpl selected = mySelectedProfile;
        if (selected != null) {
          final InspectionConfigTreeNode node = (InspectionConfigTreeNode)event.getPath().getLastPathComponent();
          final InspectionProfileImpl parentProfile = (InspectionProfileImpl)selected.getParentProfile();
          if (parentProfile != null) {
            getExpandedNodes(parentProfile).expandNode(node);
          }
          getExpandedNodes(selected).expandNode(node);
        }
      }
    });

    myTreeExpander = new DefaultTreeExpander(myTree);
    myProfileFilter = new MyFilterComponent();

    return scrollPane;
  }

  private JPopupMenu compoundPopup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final SeverityRegistrar severityRegistrar = ((SeverityProvider)mySelectedProfile.getProfileManager()).getOwnSeverityRegistrar();
    TreeSet<HighlightSeverity> severities = new TreeSet<HighlightSeverity>(severityRegistrar);
    severities.add(HighlightSeverity.ERROR);
    severities.add(HighlightSeverity.WARNING);
    severities.add(HighlightSeverity.WEAK_WARNING);
    final Collection<SeverityRegistrar.SeverityBasedTextAttributes> infoTypes =
      SeverityUtil.getRegisteredHighlightingInfoTypes(severityRegistrar);
    for (SeverityRegistrar.SeverityBasedTextAttributes info : infoTypes) {
      severities.add(info.getSeverity());
    }
    for (HighlightSeverity severity : severities) {
      final HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
      group.add(new AnAction(renderSeverity(severity), renderSeverity(severity), level.getIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          setNewHighlightingLevel(level);
        }
      });
    }
    group.add(Separator.getInstance());
    group.add(new MyAddScopeAction());
    group.add(new MyDeleteScopeAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
    return menu.getComponent();
  }

  static String renderSeverity(HighlightSeverity severity) {
    return StringUtil.capitalizeWords(severity.toString().toLowerCase(), true);
  }

  private void toggleToolNode(final InspectionConfigTreeNode toolNode) {
    final Descriptor descriptor = toolNode.getDescriptor();
    Project project = myProjectProfileManager.getProject();
    if (descriptor!= null) {
      final HighlightDisplayKey key = descriptor.getKey();
      final String toolShortName = key.toString();
      if (toolNode.isChecked()) {
        if (toolNode.getScope(project) != null){
          if (toolNode.isByDefault()) {
            mySelectedProfile.enableToolByDefault(toolShortName, project);
          }
          else {
            mySelectedProfile.enableTool(toolShortName, toolNode.getScope(project), project);
          }
        } else {
          mySelectedProfile.enableTool(toolShortName, project);
        }
      }
      else {
        if (toolNode.getScope(project) != null)  {
          if (toolNode.isByDefault()) {
            mySelectedProfile.disableToolByDefault(toolShortName, project);
          } else {
            mySelectedProfile.disableTool(toolShortName, toolNode.getScope(project), project);
          }
        } else if (toolNode.getChildCount() == 0){ //default node and no scopes configured
          mySelectedProfile.disableTool(toolShortName, project);
        }
      }
      toolNode.dropCache();
      updateUpHierarchy(toolNode, (InspectionConfigTreeNode)toolNode.getParent());
    }
    final TreePath path = new TreePath(toolNode.getPath());
    if (Comparing.equal(myTree.getSelectionPath(), path)) {
      updateOptionsAndDescriptionPanel(path);
    }
  }

  private static void updateUpHierarchy(final InspectionConfigTreeNode node, final InspectionConfigTreeNode parent) {
    if (parent != null) {
      parent.dropCache();
      updateUpHierarchy(parent, (InspectionConfigTreeNode)parent.getParent());
    }
  }

  private static boolean isDescriptorAccepted(Descriptor descriptor,
                                              @NonNls String filter,
                                              final boolean forceInclude,
                                              final List<Set<String>> keySetList, final Set<String> quoted) {
    filter = filter.toLowerCase();
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
      if (description != null && StringUtil.containsIgnoreCase(description.toLowerCase(), stripped)) {
        if (!forceInclude) return true;
      } else if (forceInclude) return false;
    }
    for (Set<String> keySet : keySetList) {
      if (keySet.contains(descriptor.getKey().toString())) {
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

  private void fillTreeData(String filter, boolean forceInclude) {
    if (mySelectedProfile == null) return;
    myRoot.removeAllChildren();
    myRoot.setChecked(false);
    myRoot.dropCache();
    List<Set<String>> keySetList = new ArrayList<Set<String>>();
    final Set<String> quoted = new HashSet<String>();
    if (filter != null && !filter.isEmpty()) {
      keySetList.addAll(SearchUtil.findKeys(filter, quoted));
    }
    Project project = myProjectProfileManager.getProject();
    for (Descriptor descriptor : myDescriptors.keySet()) {
      if (filter != null && !filter.isEmpty() && !isDescriptorAccepted(descriptor, filter, forceInclude, keySetList, quoted)) {
        continue;
      }
      final List<ScopeToolState> nonDefaultTools = mySelectedProfile.getNonDefaultTools(descriptor.getKey().toString(), project);
      final HighlightDisplayKey key = descriptor.getKey();
      final boolean enabled = mySelectedProfile.isToolEnabled(key);
      boolean hasNonDefaultScope = !nonDefaultTools.isEmpty();
      final InspectionConfigTreeNode node = new InspectionConfigTreeNode(descriptor, null, !hasNonDefaultScope, enabled, !hasNonDefaultScope);
      getGroupNode(myRoot, descriptor.getGroup()).add(node);
      if (hasNonDefaultScope) {
        for (Descriptor desc : myDescriptors.get(descriptor)) {
          node.add(new InspectionConfigTreeNode(desc, desc.getState(), false, false));
        }
        node.add(new InspectionConfigTreeNode(descriptor, descriptor.getState(), true, false));
      }
      myRoot.setEnabled(myRoot.isEnabled() || enabled);
      myRoot.dropCache();
    }
    if (filter != null && forceInclude && myRoot.getChildCount() == 0) {
      final Set<String> filters = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
      if (filters.size() > 1 || !quoted.isEmpty()) {
        fillTreeData(filter, false);
      }
    }
    TreeUtil.sort(myRoot, new InspectionsConfigTreeComparator());
  }

  private void updateOptionsAndDescriptionPanel(TreePath path) {
    if (path == null) return;
    final InspectionConfigTreeNode node = (InspectionConfigTreeNode)path.getLastPathComponent();
    final Descriptor descriptor = node.getDescriptor();
    if (descriptor != null) {
      final String description = descriptor.loadDescription();

      if (description != null) {
        // need this in order to correctly load plugin-supplied descriptions
        try {
          final HintHint hintHint = new HintHint(myBrowser, new Point(0, 0));
          hintHint.setFont(myBrowser.getFont());
          myBrowser.read(new StringReader(SearchUtil.markup(HintUtil.prepareHintText(description, hintHint), myProfileFilter.getFilter())), null);
        }
        catch (IOException e2) {
          try {
            //noinspection HardCodedStringLiteral
            myBrowser.read(new StringReader("<html><body><b>" + UNDER_CONSTRUCTION + "</b></body></html>"), null);
          }
          catch (IOException e1) {
            //Can't be
          }
        }

      }
      else {
        try {
          myBrowser.read(new StringReader(EMPTY_HTML), null);
        }
        catch (IOException e1) {
          //Can't be
        }
      }

      myOptionsPanel.removeAll();

      final NamedScope scope = node.getScope(myProjectProfileManager.getProject());
      if (scope != null || node.isInspectionNode()) {
        final HighlightDisplayKey key = descriptor.getKey();
        final LevelChooser chooser = new LevelChooser(((SeverityProvider)mySelectedProfile.getProfileManager()).getOwnSeverityRegistrar()) {
          @Override
          public Dimension getPreferredSize() {
            Dimension preferredSize = super.getPreferredSize();
            return new Dimension(Math.min(300, preferredSize.width), preferredSize.height);
          }

          @Override
          public Dimension getMinimumSize() {
            return getPreferredSize();
          }
        };
        chooser.getComboBox().addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Project project = myProjectProfileManager.getProject();
            boolean toUpdate = mySelectedProfile.getErrorLevel(key, scope, project) != chooser.getLevel();
            mySelectedProfile.setErrorLevel(key, chooser.getLevel(), node.isInspectionNode() || node.isByDefault() ? -1 : node.getParent().getIndex(node),
                                            project);
            if (toUpdate) node.dropCache();
          }
        });
        chooser.setLevel(mySelectedProfile.getErrorLevel(key, scope, myProjectProfileManager.getProject()));

        final JPanel withSeverity = new JPanel(new GridBagLayout());
        withSeverity.add(new JLabel(InspectionsBundle.message("inspection.severity")),
                         new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST,
                                                GridBagConstraints.NONE, new Insets(0, 0, 10, 10), 0, 0));
        withSeverity.add(chooser, new GridBagConstraints(1, 0, 1, 1, 1.0, 0, GridBagConstraints.WEST,
                                                         GridBagConstraints.NONE, new Insets(0, 0, 10, 0), 0, 0));

        final JComponent comp = descriptor.getState().getAdditionalConfigPanel();
        withSeverity.add(comp,
                         new GridBagConstraints(0, 1, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        myOptionsPanel.add(withSeverity, BorderLayout.CENTER);
      }
      myOptionsPanel.revalidate();
      GuiUtils.enableChildren(myOptionsPanel, node.isChecked());
    }
    else {
      initOptionsAndDescriptionPanel();
    }
    myOptionsPanel.repaint();
  }

  private void initOptionsAndDescriptionPanel() {
    myOptionsPanel.removeAll();
    try {
      myBrowser.read(new StringReader(EMPTY_HTML), null);
    }
    catch (IOException e1) {
      //Can't be
    }
    myOptionsPanel.validate();
    myOptionsPanel.repaint();
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
    InspectionConfigTreeNode child = new InspectionConfigTreeNode(group, null, false, false);
    root.add(child);
    return child;
  }

  public boolean setSelectedProfileModified(boolean modified) {
    mySelectedProfile.setModified(modified);
    return modified;
  }

  ModifiableModel getSelectedProfile() {
    return mySelectedProfile;
  }

  private void setSelectedProfile(final ModifiableModel modifiableModel) {
    if (mySelectedProfile == modifiableModel) return;
    mySelectedProfile = (InspectionProfileImpl)modifiableModel;
    if (mySelectedProfile != null) {
      myInitialProfile = mySelectedProfile.getName();
    }
    initDescriptors();
    filterTree(myProfileFilter != null ? myProfileFilter.getFilter() : null);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(700, 500);
  }

  public void disposeUI() {
    if (myInspectionProfilePanel == null) {
      return;
    }
    myProperties.setFloat(VERTICAL_DIVIDER_PROPORTION, myMainSplitter.getProportion());
    myProperties.setFloat(HORIZONTAL_DIVIDER_PROPORTION, myRightSplitter.getProportion());
    myAlarm.cancelAllRequests();
    myProfileFilter.dispose();
    if (mySelectedProfile != null) {
      for (ScopeToolState state : mySelectedProfile.getAllTools(myProjectProfileManager.getProject())) {
        state.resetConfigPanel();
      }
    }
    mySelectedProfile = null;
  }

  private JPanel createInspectionProfileSettingsPanel() {

    myBrowser = new JEditorPane(UIUtil.HTML_MIME, EMPTY_HTML);
    myBrowser.setEditable(false);
    myBrowser.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 5));
    myBrowser.addHyperlinkListener(new BrowserHyperlinkListener());

    initDescriptors();
    fillTreeData(myProfileFilter != null ? myProfileFilter.getFilter() : null, true);

    JPanel descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.setBorder(IdeBorderFactory.createTitledBorder(InspectionsBundle.message("inspection.description.title"), false,
                                                                   new Insets(13, 0, 0, 0)));
    descriptionPanel.add(ScrollPaneFactory.createScrollPane(myBrowser), BorderLayout.CENTER);

    myRightSplitter = new Splitter(true);
    myRightSplitter.setFirstComponent(descriptionPanel);
    myRightSplitter.setProportion(myProperties.getFloat(HORIZONTAL_DIVIDER_PROPORTION, 0.5f));

    myOptionsPanel = new JPanel(new BorderLayout());
    myOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder("Options", false,
                                                                   new Insets(0, 0, 0, 0)));
    initOptionsAndDescriptionPanel();
    myRightSplitter.setSecondComponent(myOptionsPanel);
    myRightSplitter.setHonorComponentsMinimumSize(true);

    final JPanel treePanel = new JPanel(new BorderLayout());
    final JScrollPane tree = initTreeScrollPane();
    treePanel.add(tree, BorderLayout.CENTER);

    final JPanel northPanel = new JPanel(new GridBagLayout());
    northPanel.setBorder(IdeBorderFactory.createEmptyBorder(2, 0, 2, 0));
    northPanel.add(createTreeToolbarPanel().getComponent(), new GridBagConstraints(0, 0, 1, 1, 0.5, 1, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    northPanel.add(myProfileFilter, new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.BASELINE_TRAILING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    treePanel.add(northPanel, BorderLayout.NORTH);

    myMainSplitter = new Splitter(false);
    myMainSplitter.setFirstComponent(treePanel);
    myMainSplitter.setSecondComponent(myRightSplitter);
    myMainSplitter.setHonorComponentsMinimumSize(false);
    myMainSplitter.setProportion(myProperties.getFloat(VERTICAL_DIVIDER_PROPORTION, 0.5f));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myMainSplitter, BorderLayout.CENTER);
    return panel;
  }

  public boolean isModified() {
    if (myModified) return true;
    if (mySelectedProfile.isChanged()) return true;
    if (myShareProfile != (mySelectedProfile.getProfileManager() == myProjectProfileManager)) return true;
    if (!Comparing.strEqual(myInitialProfile, mySelectedProfile.getName())) return true;
    if (descriptorsAreChanged()) {
      return true;
    }
    return false;
  }

  public void reset() {
    myModified = false;
    setSelectedProfile(mySelectedProfile);
    final String filter = myProfileFilter.getFilter();
    myProfileFilter.reset();
    myProfileFilter.setSelectedItem(filter);
    myShareProfile = mySelectedProfile.getProfileManager() == myProjectProfileManager;
  }

  public void apply() throws ConfigurationException {
    final boolean modified = isModified();
    if (!modified) {
      return;
    }
    final ModifiableModel selectedProfile = getSelectedProfile();
    final ProfileManager profileManager =
      myShareProfile ? myProjectProfileManager : InspectionProfileManager.getInstance();
    selectedProfile.setLocal(!myShareProfile);
    if (selectedProfile.getProfileManager() != profileManager) {
      if (selectedProfile.getProfileManager().getProfile(selectedProfile.getName(), false) != null) {
        selectedProfile.getProfileManager().deleteProfile(selectedProfile.getName());
      }
      copyUsedSeveritiesIfUndefined(selectedProfile, profileManager);
      selectedProfile.setProfileManager(profileManager);
    }
    final InspectionProfile parentProfile = selectedProfile.getParentProfile();

    if (((InspectionProfileManagerImpl)InspectionProfileManager.getInstance()).getSchemesManager().isShared(selectedProfile)) {
      if (descriptorsAreChanged()) {
        throw new ConfigurationException("Shared profile cannot be modified. Please do \"Save As...\" first.");
      }

    }

    try {
      selectedProfile.commit();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }
    setSelectedProfile(parentProfile.getModifiableModel());
    setSelectedProfileModified(false);
    myModified = false;
  }

  private static void copyUsedSeveritiesIfUndefined(final ModifiableModel selectedProfile, final ProfileManager profileManager) {
    final SeverityRegistrar registrar = ((SeverityProvider)profileManager).getSeverityRegistrar();
    final Set<HighlightSeverity> severities = ((InspectionProfileImpl)selectedProfile).getUsedSeverities();
    for (Iterator<HighlightSeverity> iterator = severities.iterator(); iterator.hasNext();) {
      HighlightSeverity severity = iterator.next();
      if (registrar.isSeverityValid(severity.toString())) {
        iterator.remove();
      }
    }

    if (!severities.isEmpty()) {
      final SeverityRegistrar oppositeRegister = ((SeverityProvider)selectedProfile.getProfileManager()).getSeverityRegistrar();
      for (HighlightSeverity severity : severities) {
        final TextAttributesKey attributesKey = TextAttributesKey.find(severity.toString());
        final TextAttributes textAttributes = oppositeRegister.getTextAttributesBySeverity(severity);
        LOG.assertTrue(textAttributes != null, severity.toString());
        HighlightInfoType.HighlightInfoTypeImpl info = new HighlightInfoType.HighlightInfoTypeImpl(severity, attributesKey);
        registrar.registerSeverity(new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes.clone(), info),
                                   textAttributes.getErrorStripeColor());
      }
    }
  }

  private boolean descriptorsAreChanged() {
    for (Map.Entry<Descriptor, List<Descriptor>> entry : myDescriptors.entrySet()) {
      Descriptor desc = entry.getKey();
      Project project = myProjectProfileManager.getProject();
      if (mySelectedProfile.isToolEnabled(desc.getKey(), (NamedScope)null, project) != desc.isEnabled()){
        return true;
      }
      if (mySelectedProfile.getErrorLevel(desc.getKey(), desc.getScope(), project) != desc.getLevel()) {
        return true;
      }
      final List<Descriptor> descriptors = entry.getValue();
      for (Descriptor descriptor : descriptors) {
        if (mySelectedProfile.isToolEnabled(descriptor.getKey(), descriptor.getScope(), project) != descriptor.isEnabled()) {
          return true;
        }
        if (mySelectedProfile.getErrorLevel(descriptor.getKey(), descriptor.getScope(), project) != descriptor.getLevel()) {
          return true;
        }
      }

      final List<ScopeToolState> tools = mySelectedProfile.getNonDefaultTools(desc.getKey().toString(), project);
      if (tools.size() != descriptors.size()) {
        return true;
      }
      for (int i = 0; i < tools.size(); i++) {
        final ScopeToolState pair = tools.get(i);
        if (!Comparing.equal(pair.getScope(project), descriptors.get(i).getScope())) {
          return true;
        }
      }
    }


    return false;
  }

  public Tree getTree() {
    return myTree;
  }

  public boolean isProfileShared() {
    return myShareProfile;
  }

  public void setProfileShared(boolean profileShared) {
    myShareProfile = profileShared;
  }

  @Override
  public void setVisible(boolean aFlag) {
    if (aFlag && myInspectionProfilePanel == null) {
      initUI();
    }
    super.setVisible(aFlag);
  }

  private void setNewHighlightingLevel(@NotNull HighlightDisplayLevel level) {
    final int[] rows = myTree.getSelectionRows();
    final boolean showOptionsAndDescriptorPanels = rows != null && rows.length == 1;
    for (int i = 0; rows != null && i < rows.length; i++) {
      final InspectionConfigTreeNode node = (InspectionConfigTreeNode)myTree.getPathForRow(rows[i]).getLastPathComponent();
      final InspectionConfigTreeNode parent = (InspectionConfigTreeNode)node.getParent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof Descriptor && (node.getScopeName() != null || node.isLeaf())) {
        updateErrorLevel(node, showOptionsAndDescriptorPanels, level);
        updateUpHierarchy(node, parent);
      }
      else {
        updateErrorLevelUpInHierarchy(level, showOptionsAndDescriptorPanels, node);
        updateUpHierarchy(node, parent);
      }
    }
    if (rows != null && rows.length == 1) {
      updateOptionsAndDescriptionPanel(myTree.getPathForRow(rows[0]));
    }
    else {
      initOptionsAndDescriptionPanel();
    }
    repaintTableData();
  }

  private void updateErrorLevelUpInHierarchy(@NotNull HighlightDisplayLevel level,
                                             boolean showOptionsAndDescriptorPanels,
                                             InspectionConfigTreeNode node) {
    node.dropCache();
    for (int j = 0; j < node.getChildCount(); j++) {
      final InspectionConfigTreeNode child = (InspectionConfigTreeNode)node.getChildAt(j);
      final Object userObject = child.getUserObject();
      if (userObject instanceof Descriptor && (child.getScopeName() != null || child.isLeaf())) {
        updateErrorLevel(child, showOptionsAndDescriptorPanels, level);
      }
      else {
        updateErrorLevelUpInHierarchy(level, showOptionsAndDescriptorPanels, child);
      }
    }
  }

  private void updateErrorLevel(final InspectionConfigTreeNode child,
                                final boolean showOptionsAndDescriptorPanels,
                                @NotNull HighlightDisplayLevel level) {
    final HighlightDisplayKey key = child.getDescriptor().getKey();
    mySelectedProfile.setErrorLevel(key, level, child.isInspectionNode() || child.isByDefault() ? -1 : child.getParent().getIndex(child),
                                    myProjectProfileManager.getProject());
    child.dropCache();
    if (showOptionsAndDescriptorPanels) {
      updateOptionsAndDescriptionPanel(new TreePath(child.getPath()));
    }
  }

  private class MyFilterComponent extends FilterComponent {
    private MyFilterComponent() {
      super(INSPECTION_FILTER_HISTORY, 10);
      setHistory(Arrays.asList("\"New in 13\""));
    }

    @Override
    public void filter() {
      filterTree(getFilter());
    }

    @Override
    protected void onlineFilter() {
      if (mySelectedProfile == null) return;
      final String filter = getFilter();
      getExpandedNodes(mySelectedProfile).saveVisibleState(myTree);
      fillTreeData(filter, true);
      reloadModel();
      if (filter == null || filter.isEmpty()) {
        restoreTreeState();
      } else {
        TreeUtil.expandAll(myTree);
      }
    }
  }

  private class MyAddScopeAction extends AddScopeAction {
    public MyAddScopeAction() {
      super(SingleInspectionProfilePanel.this.myTree);
    }

    @Override
    protected InspectionProfileImpl getSelectedProfile() {
      return mySelectedProfile;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      super.actionPerformed(e);
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths != null && paths.length == 1) {
        updateOptionsAndDescriptionPanel(myTree.getSelectionPath());
      } else {
        initOptionsAndDescriptionPanel();
      }
    }
  }

  private class MyDeleteScopeAction extends DeleteScopeAction {
    public MyDeleteScopeAction() {
      super(SingleInspectionProfilePanel.this.myTree);
    }

    @Override
    protected InspectionProfileImpl getSelectedProfile() {
      return mySelectedProfile;
    }
  }
}
