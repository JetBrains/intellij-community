package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ant.AntConfiguration;
import com.intellij.ant.BuildFile;
import com.intellij.ant.actions.TargetAction;
import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tools.Tool;
import com.intellij.tools.ToolManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

import org.jetbrains.annotations.NonNls;

public class ActionsTreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil");

  public static final String MAIN_MENU_TITLE = KeyMapBundle.message("main.menu.action.title");
  public static final String MAIN_TOOLBAR = KeyMapBundle.message("main.toolbar.title");
  public static final String EDITOR_POPUP = KeyMapBundle.message("editor.popup.menu.title");

  private static final Icon MAIN_MENU_ICON = IconLoader.getIcon("/nodes/keymapMainMenu.png");
  private static final Icon EDITOR_ICON = IconLoader.getIcon("/nodes/keymapEditor.png");
  private static final Icon EDITOR_OPEN_ICON = IconLoader.getIcon("/nodes/keymapEditorOpen.png");
  private static final Icon ANT_ICON = IconLoader.getIcon("/nodes/keymapAnt.png");
  private static final Icon ANT_OPEN_ICON = IconLoader.getIcon("/nodes/keymapAntOpen.png");
  private static final Icon TOOLS_ICON = IconLoader.getIcon("/nodes/keymapTools.png");
  private static final Icon TOOLS_OPEN_ICON = IconLoader.getIcon("/nodes/keymapToolsOpen.png");
  private static final Icon OTHER_ICON = IconLoader.getIcon("/nodes/keymapOther.png");
  public static final String EDITOR_TAB_POPUP = KeyMapBundle.message("editor.tab.popup.menu.title");
  public static final String FAVORITES_POPUP = KeyMapBundle.message("favorites.popup.title");
  public static final String PROJECT_VIEW_POPUP = KeyMapBundle.message("project.view.popup.menu.title");
  public static final String COMMANDER_POPUP = KeyMapBundle.message("commender.view.popup.menu.title");
  public static final String STRUCTURE_VIEW_POPUP = KeyMapBundle.message("structure.view.popup.menu.title");
  public static final String J2EE_POPUP = KeyMapBundle.message("j2ee.view.popup.menu.title");

  @NonNls
  private static final String VCS_GROUP_ID = "VcsGroup";
  @NonNls
  private static final String EDITOR_PREFIX = "Editor";

  private ActionsTreeUtil() {}

  public static Group createMainGroup(final Project project, final Keymap keymap, final QuickList[] quickLists) {
    Group mainGroup = new Group(KeyMapBundle.message("all.actions.group.title"), null, null);
    mainGroup.addGroup(createEditorActionsGroup());
    mainGroup.addGroup(createMainMenuGroup(false));
    mainGroup.addGroup(createVcsGroup());
    mainGroup.addGroup(createAntGroup(project));
    mainGroup.addGroup(createDebuggerActionsGroup());
    mainGroup.addGroup(createGuiDesignerActionsGroup(mainGroup));
    mainGroup.addGroup(createBookmarksActionsGroup());
    mainGroup.addGroup(createExternalToolsGroup());
    mainGroup.addGroup(createMacrosGroup());
    mainGroup.addGroup(createQuickListsGroup(quickLists));

    Group otherGroup = createOtherGroup(mainGroup, keymap);
    mainGroup.addGroup(otherGroup);

    mainGroup.addGroup(createPluginsActionsGroup());
    return mainGroup;
  }

  private static Group createPluginsActionsGroup() {
    Group pluginsGroup = new Group(KeyMapBundle.message("plugins.group.title"), null, null);
    ActionManagerEx managerEx = ActionManagerEx.getInstanceEx();
    final PluginDescriptor[] plugins = PluginManager.getPlugins();
    for (int i = 0; i < plugins.length; i++) {
      PluginDescriptor plugin = plugins[i];
      Group pluginGroup = new Group(plugin.getName(), null, null);
      final String[] pluginActions = managerEx.getPluginActions(plugin.getPluginId());
      if (pluginActions == null || pluginActions.length == 0){
        continue;
      }
      for (int j = 0; j < pluginActions.length; j++) {
        String pluginAction = pluginActions[j];
        pluginGroup.addActionId(pluginAction);
      }
      pluginsGroup.addGroup(pluginGroup);
    }
    return pluginsGroup;
  }

  private static Group createBookmarksActionsGroup() {
    return createGroup((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_BOOKMARKS), true);
  }

  private static Group createGuiDesignerActionsGroup(Group mainGroup) {
    mainGroup.initIds();
    Group group = new Group(KeyMapBundle.message("gui.designer.group.title"), IdeActions.GROUP_GUI_DESIGNER_EDITOR_POPUP, null, null);
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup uiGroup = (ActionGroup)actionManager.getAction(IdeActions.GROUP_GUI_DESIGNER_EDITOR_POPUP);
    AnAction[] actions = uiGroup.getChildren(null);
    for (int i = 0; i < actions.length; i++) {
      AnAction action = actions[i];
      String actionId = actionManager.getId(action);
      if (actionId == null || mainGroup.containsId(actionId)) continue;
      group.addActionId(actionId);
    }
    return group;
  }

  private static Group createVcsGroup() {
    Group group = new Group(KeyMapBundle.message("version.control.group.title"), VCS_GROUP_ID, null, null);
    ActionGroup versionControls = (ActionGroup)ActionManager.getInstance().getAction(VCS_GROUP_ID);
    fillGroupIgnorePopupFlag(versionControls, group, false);
    return group;
  }

  public static Group createMainMenuGroup(boolean ignore) {
    Group group = new Group(MAIN_MENU_TITLE, IdeActions.GROUP_MAIN_MENU, MAIN_MENU_ICON, MAIN_MENU_ICON);
    ActionGroup mainMenuGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU);
    fillGroupIgnorePopupFlag(mainMenuGroup, group, ignore);
    return group;
  }

  public static void fillGroupIgnorePopupFlag(ActionGroup actionGroup, Group group, boolean ignore) {
    AnAction[] mainMenuTopGroups = actionGroup.getChildren(null);
    for (int i = 0; i < mainMenuTopGroups.length; i++) {
      AnAction action = mainMenuTopGroups[i];
      Group subGroup = createGroup((ActionGroup)action, ignore);
      if (subGroup.getSize() > 0) {
        group.addGroup(subGroup);
      }
    }
  }

  public static Group createGroup(ActionGroup actionGroup, boolean ignore) {
    final String name = actionGroup.getTemplatePresentation().getText();
    return createGroup(actionGroup, name != null ? name : ActionManager.getInstance().getId(actionGroup), null, null, ignore);
  }

  public static Group createGroup(ActionGroup actionGroup, String groupName, Icon icon, Icon openIcon, boolean ignore) {
    ActionManager actionManager = ActionManager.getInstance();
    Group group = new Group(groupName, actionManager.getId(actionGroup), icon, openIcon);
    AnAction[] children = actionGroup.getChildren(null);

    for (int i = 0; i < children.length; i++) {
      AnAction action = children[i];

      if (action instanceof ActionGroup) {
        Group subGroup = createGroup((ActionGroup)action, ignore);
        if (subGroup.getSize() > 0) {
          if (!ignore && !((ActionGroup)action).isPopup()) {
            group.addAll(subGroup);
          }
          else {
            group.addGroup(subGroup);
          }
        }
        else {
          group.addGroup(subGroup);
        }
      }
      else if (action instanceof Separator){
        if (group.getSize() > 0 && i < children.length - 1 && !(group.getChildren().get(group.getSize() - 1) instanceof Separator)) {
          group.addSeparator();
        }
      }
      else {
        String id = actionManager.getId(action);
        if (id != null) {
          if (id.startsWith(TargetAction.ACTION_ID_PREFIX)) continue;
          if (id.startsWith(Tool.ACTION_ID_PREFIX)) continue;
          group.addActionId(id);
        }
      }
    }
    group.normalizeSeparators();
    return group;
  }

  private static Group createDebuggerActionsGroup() {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup debuggerGroup = (ActionGroup) actionManager.getAction(IdeActions.GROUP_DEBUGGER);
    AnAction[] debuggerActions = debuggerGroup.getChildren(null);

    ArrayList<String> ids = new ArrayList<String>();
    for (int i = 0; i < debuggerActions.length; i++) {
      AnAction editorAction = debuggerActions[i];
      String actionId = actionManager.getId(editorAction);
      ids.add(actionId);
    }

    Collections.sort(ids);
    Group group = new Group(KeyMapBundle.message("debugger.actions.group.title"), IdeActions.GROUP_DEBUGGER, null, null);
    for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
      String id = iterator.next();
      group.addActionId(id);
    }

    return group;
  }

  private static Group createEditorActionsGroup() {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup editorGroup = (ActionGroup) actionManager.getAction(IdeActions.GROUP_EDITOR);
    AnAction[] editorActions = editorGroup.getChildren(null);

    ArrayList<String> ids = new ArrayList<String>();
    for (int i = 0; i < editorActions.length; i++) {
      AnAction editorAction = editorActions[i];
      String actionId = actionManager.getId(editorAction);
      if (actionId == null) continue;
      if (actionId.startsWith(EDITOR_PREFIX)) {
        AnAction action = actionManager.getAction('$' + actionId.substring(6));
        if (action != null) continue;
      }
      ids.add(actionId);
    }

    Collections.sort(ids);
    Group group = new Group(KeyMapBundle.message("editor.actions.group.title"), IdeActions.GROUP_EDITOR, EDITOR_ICON, EDITOR_OPEN_ICON);
    for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
      String id = iterator.next();
      group.addActionId(id);
    }

    return group;
  }

  private static Group createAntGroup(final Project project) {
    String[] ids = ActionManagerEx.getInstanceEx().getActionIds(TargetAction.ACTION_ID_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("ant.targets.group.title"), ANT_ICON, ANT_OPEN_ICON);

    if (project != null) {
      AntConfiguration antConfiguration = AntConfiguration.getInstance(project);

      com.intellij.util.containers.HashMap buildFileToGroup = new com.intellij.util.containers.HashMap();
      for (int i = 0; i < ids.length; i++) {
        String id = ids[i];

        BuildFile buildFile = antConfiguration.findBuildFileByActionId(id);
        if (buildFile == null) {
          LOG.info("no buildfile found for actionId=" + id);
          continue;
        }

        Group subGroup = (Group)buildFileToGroup.get(buildFile);
        if (subGroup == null) {
          subGroup = new Group(buildFile.getPresentableName(), null, null);
          buildFileToGroup.put(buildFile, subGroup);
          group.addGroup(subGroup);
        }

        subGroup.addActionId(id);
      }
    }
    return group;
  }

  private static Group createMacrosGroup() {
    String[] ids = ActionManagerEx.getInstanceEx().getActionIds(ActionMacro.MACRO_ACTION_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("macros.group.title"), null, null);
    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      group.addActionId(id);
    }
    return group;
  }

  private static Group createQuickListsGroup(final QuickList[] quickLists) {
    Arrays.sort(quickLists, new Comparator<QuickList>() {
      public int compare(QuickList l1, QuickList l2) {
        return l1.getActionId().compareTo(l2.getActionId());
      }
    });

    Group group = new Group(KeyMapBundle.message("quick.lists.group.title"), null, null);
    for (int i = 0; i < quickLists.length; i++) {
      group.addQuickList(quickLists[i]);
    }
    return group;
  }


  private static Group createExternalToolsGroup() {
    String[] ids = ActionManagerEx.getInstanceEx().getActionIds(Tool.ACTION_ID_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("actions.tree.external.tools.group"), TOOLS_ICON, TOOLS_OPEN_ICON);

    ToolManager toolManager = ToolManager.getInstance();

    com.intellij.util.containers.HashMap toolGroupNameToGroup = new com.intellij.util.containers.HashMap();

    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];

      String groupName = toolManager.getGroupByActionId(id);

      if (groupName != null && groupName.trim().length() == 0) {
        groupName = null;
      }

      Group subGroup = (Group)toolGroupNameToGroup.get(groupName);
      if (subGroup == null) {
        subGroup = new Group(groupName, null, null);
        toolGroupNameToGroup.put(groupName, subGroup);
        if (groupName != null) {
          group.addGroup(subGroup);
        }
      }

      subGroup.addActionId(id);
    }

    Group subGroup = (Group)toolGroupNameToGroup.get(null);
    if (subGroup != null) {
      group.addAll(subGroup);
    }

    return group;
  }

  private static Group createOtherGroup(Group addedActions, final Keymap keymap) {
    addedActions.initIds();
    ArrayList<String> result = new ArrayList<String>();

    if (keymap != null) {
      String[] actionIds = keymap.getActionIds();
      for(int i = 0; i < actionIds.length; i++){
        String id = actionIds[i];

        if (id.startsWith(EDITOR_PREFIX)) {
          AnAction action = ActionManager.getInstance().getAction("$" + id.substring(6));
          if (action != null) continue;
        }

        if (!id.startsWith(QuickList.QUICK_LIST_PREFIX) && !addedActions.containsId(id)) {
          result.add(id);
        }
      }
    }

    // add all registered actions
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    String[] registeredActionIds = actionManager.getActionIds("");
    for (int i = 0; i < registeredActionIds.length; i++) {
      String id = registeredActionIds[i];
      if (actionManager.getAction(id) instanceof ActionGroup){
        continue;
      }
      if (!id.startsWith(QuickList.QUICK_LIST_PREFIX) && !addedActions.containsId(id) && !result.contains(id)) {
        result.add(id);
      }
    }

    filterOtherActionsGroup(result);

    Collections.sort(
      result, new Comparator<String>() {
        public int compare(String id1, String id2) {
          return getTextToCompare(id1).compareToIgnoreCase(getTextToCompare(id2));
        }

        private String getTextToCompare(String id) {
          AnAction action = actionManager.getAction(id);
          if (action == null){
            return id;
          }
          String text = action.getTemplatePresentation().getText();
          return text != null ? text: id;
        }
      });

    Group group = new Group(KeyMapBundle.message("other.group.title"), OTHER_ICON, OTHER_ICON);
    for (int i = 0; i < result.size(); i++) {
      group.addActionId(result.get(i));
    }
    return group;
  }

  private static void filterOtherActionsGroup(ArrayList<String> actions) {
    filterOutGroup(actions, IdeActions.GROUP_GENERATE);
    filterOutGroup(actions, IdeActions.GROUP_NEW);
    filterOutGroup(actions, IdeActions.GROUP_CHANGE_SCHEME);
  }

  private static void filterOutGroup(ArrayList<String> actions, String groupId) {
    if (groupId == null) {
      throw new IllegalArgumentException();
    }
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(groupId);
    if (action instanceof DefaultActionGroup) {
      DefaultActionGroup group = (DefaultActionGroup)action;
      AnAction[] children = group.getChildren(null);
      for (int i = 0; i < children.length; i++) {
        AnAction child = children[i];
        String childId = actionManager.getId(child);
        if (childId == null) {
          // SCR 35149
          continue;
        }
        if (child instanceof DefaultActionGroup) {
          filterOutGroup(actions, childId);
        }
        else {
          actions.remove(childId);
        }
      }
    }
  }

  public static DefaultMutableTreeNode createNode(Group group) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(group);
    for (Iterator iterator = group.getChildren().iterator(); iterator.hasNext();) {
      Object child = iterator.next();
      if (child instanceof Group) {
        DefaultMutableTreeNode childNode = createNode((Group)child);
        node.add(childNode);
      }
      else {
        node.add(new DefaultMutableTreeNode(child));
      }
    }
    return node;
  }

}