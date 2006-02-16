package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ant.AntConfiguration;
import com.intellij.ant.BuildFile;
import com.intellij.ant.actions.TargetAction;
import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tools.Tool;
import com.intellij.tools.ToolManager;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

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

  private static Group createPluginsActionsGroup(final String filter, final boolean forceFiltering) {
    Group pluginsGroup = new Group(KeyMapBundle.message("plugins.group.title"), null, null);
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    ActionManagerEx managerEx = ActionManagerEx.getInstanceEx();
    final IdeaPluginDescriptor[] plugins = ApplicationManager.getApplication().getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      Group pluginGroup = new Group(plugin.getName(), null, null);
      final String[] pluginActions = managerEx.getPluginActions(plugin.getPluginId());
      if (pluginActions == null || pluginActions.length == 0) {
        continue;
      }
      for (String pluginAction : pluginActions) {
        if (keymapManager.getBoundActions().contains(pluginAction)) continue;
        final AnAction anAction = managerEx.getAction(pluginAction);
        if (isActionFiltered(filter, anAction, forceFiltering)){
          pluginGroup.addActionId(pluginAction);
        }
      }
      if (pluginGroup.getSize() > 0) {
        pluginsGroup.addGroup(pluginGroup);
      }
    }
    return pluginsGroup;
  }

  private static Group createBookmarksActionsGroup(final String filter, final boolean forceFiltering) {
    return createGroup((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_BOOKMARKS), true, filter, forceFiltering);
  }

  private static Group createGuiDesignerActionsGroup(final String filter, final boolean forceFiltering, Group mainGroup) {
    mainGroup.initIds();
    Group group = new Group(KeyMapBundle.message("gui.designer.group.title"), IdeActions.GROUP_GUI_DESIGNER_EDITOR_POPUP, null, null);
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup uiGroup = (ActionGroup)actionManager.getAction(IdeActions.GROUP_GUI_DESIGNER_EDITOR_POPUP);
    AnAction[] actions = uiGroup.getChildren(null);
    for (AnAction action : actions) {
      String actionId = actionManager.getId(action);
      if (actionId == null || mainGroup.containsId(actionId)) continue;
      if (isActionFiltered(filter, action, forceFiltering)) {
        group.addActionId(actionId);
      }
    }
    return group;
  }

  private static Group createVcsGroup(final String filter, final boolean forceFiltering) {
    Group group = new Group(KeyMapBundle.message("version.control.group.title"), VCS_GROUP_ID, null, null);
    ActionGroup versionControls = (ActionGroup)ActionManager.getInstance().getAction(VCS_GROUP_ID);
    fillGroupIgnorePopupFlag(versionControls, group, false, filter, forceFiltering);
    return group;
  }

  public static Group createMainMenuGroup(final String filter, final boolean forceFiltering, boolean ignore) {
    Group group = new Group(MAIN_MENU_TITLE, IdeActions.GROUP_MAIN_MENU, MAIN_MENU_ICON, MAIN_MENU_ICON);
    ActionGroup mainMenuGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU);
    fillGroupIgnorePopupFlag(mainMenuGroup, group, ignore, filter, forceFiltering);
    return group;
  }

  public static void fillGroupIgnorePopupFlag(ActionGroup actionGroup, Group group, boolean ignore, String filter, boolean forceFiltering) {
    AnAction[] mainMenuTopGroups = actionGroup.getChildren(null);
    for (AnAction action : mainMenuTopGroups) {
      Group subGroup = createGroup((ActionGroup)action, ignore, filter, forceFiltering);
      if (subGroup.getSize() > 0) {
        group.addGroup(subGroup);
      }
    }
  }

  public static Group createGroup(ActionGroup actionGroup, boolean ignore, final String filter, final boolean forceFiltering) {
    final String name = actionGroup.getTemplatePresentation().getText();
    return createGroup(actionGroup, name != null ? name : ActionManager.getInstance().getId(actionGroup), null, null, ignore, filter, forceFiltering);
  }

  public static Group createGroup(ActionGroup actionGroup,
                                  String groupName,
                                  Icon icon,
                                  Icon openIcon,
                                  boolean ignore,
                                  final String filter,
                                  final boolean forceFiltering) {
    ActionManager actionManager = ActionManager.getInstance();
    Group group = new Group(groupName, actionManager.getId(actionGroup), icon, openIcon);
    AnAction[] children = actionGroup.getChildren(null);

    for (int i = 0; i < children.length; i++) {
      AnAction action = children[i];

      if (action instanceof ActionGroup) {
        Group subGroup = createGroup((ActionGroup)action, ignore, filter, forceFiltering);
        if (subGroup.getSize() > 0) {
          if (!ignore && !((ActionGroup)action).isPopup()) {
            group.addAll(subGroup);
          }
          else {
            group.addGroup(subGroup);
          }
        }
        /*else {
          group.addGroup(subGroup);
        }*/
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
          if (isActionFiltered(filter, action, forceFiltering)) {
            group.addActionId(id);
          }
        }
      }
    }
    group.normalizeSeparators();
    return group;
  }

  private static Group createDebuggerActionsGroup(final String filter, final boolean forceFiltering) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup debuggerGroup = (ActionGroup) actionManager.getAction(IdeActions.GROUP_DEBUGGER);
    AnAction[] debuggerActions = debuggerGroup.getChildren(null);

    ArrayList<String> ids = new ArrayList<String>();
    for (AnAction editorAction : debuggerActions) {
      String actionId = actionManager.getId(editorAction);
      if (isActionFiltered(filter, editorAction, forceFiltering)) {
        ids.add(actionId);
      }
    }

    Collections.sort(ids);
    Group group = new Group(KeyMapBundle.message("debugger.actions.group.title"), IdeActions.GROUP_DEBUGGER, null, null);
    for (String id : ids) {
      group.addActionId(id);
    }

    return group;
  }

  private static Group createEditorActionsGroup(final String filter, final boolean forceFiltering) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup editorGroup = (ActionGroup) actionManager.getAction(IdeActions.GROUP_EDITOR);
    AnAction[] editorActions = editorGroup.getChildren(null);

    ArrayList<String> ids = new ArrayList<String>();
    for (AnAction editorAction : editorActions) {
      String actionId = actionManager.getId(editorAction);
      if (actionId == null) continue;
      if (actionId.startsWith(EDITOR_PREFIX)) {
        AnAction action = actionManager.getAction('$' + actionId.substring(6));
        if (action != null) continue;
      }
      if (isActionFiltered(filter, editorAction, forceFiltering)) {
        ids.add(actionId);
      }
    }

    Collections.sort(ids);
    Group group = new Group(KeyMapBundle.message("editor.actions.group.title"), IdeActions.GROUP_EDITOR, EDITOR_ICON, EDITOR_OPEN_ICON);
    for (String id : ids) {
      group.addActionId(id);
    }

    return group;
  }

  private static Group createAntGroup(final String filter, final boolean forceFiltering, final Project project) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    String[] ids = actionManager.getActionIds(TargetAction.ACTION_ID_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("ant.targets.group.title"), ANT_ICON, ANT_OPEN_ICON);

    if (project != null) {
      AntConfiguration antConfiguration = AntConfiguration.getInstance(project);

      HashMap<BuildFile,Group> buildFileToGroup = new HashMap<BuildFile, Group>();
      for (String id : ids) {
        if (!isActionFiltered(filter, actionManager.getAction(id), forceFiltering)) continue;
        BuildFile buildFile = antConfiguration.findBuildFileByActionId(id);
        if (buildFile == null) {
          LOG.info("no buildfile found for actionId=" + id);
          continue;
        }

        Group subGroup = buildFileToGroup.get(buildFile);
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

  private static Group createMacrosGroup(final String filter, final boolean forceFiltering) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    String[] ids = actionManager.getActionIds(ActionMacro.MACRO_ACTION_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("macros.group.title"), null, null);
    for (String id : ids) {
      if (isActionFiltered(filter, actionManager.getAction(id), forceFiltering)) {
        group.addActionId(id);
      }
    }
    return group;
  }

  private static Group createQuickListsGroup(final String filter, final boolean forceFiltering, final QuickList[] quickLists) {
    Arrays.sort(quickLists, new Comparator<QuickList>() {
      public int compare(QuickList l1, QuickList l2) {
        return l1.getActionId().compareTo(l2.getActionId());
      }
    });

    Group group = new Group(KeyMapBundle.message("quick.lists.group.title"), null, null);
    for (QuickList quickList : quickLists) {
      if (SearchUtil.isComponentHighlighted(quickList.getDisplayName(), filter, forceFiltering, ApplicationManager.getApplication().getComponent(KeymapConfigurable.class))) {
        group.addQuickList(quickList);
      }
    }
    return group;
  }


  private static Group createExternalToolsGroup(final String filter, final boolean forceFiltering) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    String[] ids = actionManager.getActionIds(Tool.ACTION_ID_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("actions.tree.external.tools.group"), TOOLS_ICON, TOOLS_OPEN_ICON);

    ToolManager toolManager = ToolManager.getInstance();

    HashMap<String,Group> toolGroupNameToGroup = new HashMap<String, Group>();

    for (String id : ids) {
      if (!isActionFiltered(filter, actionManager.getAction(id), forceFiltering)) continue;
      String groupName = toolManager.getGroupByActionId(id);

      if (groupName != null && groupName.trim().length() == 0) {
        groupName = null;
      }

      Group subGroup = toolGroupNameToGroup.get(groupName);
      if (subGroup == null) {
        subGroup = new Group(groupName, null, null);
        toolGroupNameToGroup.put(groupName, subGroup);
        if (groupName != null) {
          group.addGroup(subGroup);
        }
      }

      subGroup.addActionId(id);
    }

    Group subGroup = toolGroupNameToGroup.get(null);
    if (subGroup != null) {
      group.addAll(subGroup);
    }

    return group;
  }

  private static Group createOtherGroup(final String filter, final boolean forceFiltering, Group addedActions, final Keymap keymap) {
    addedActions.initIds();
    ArrayList<String> result = new ArrayList<String>();

    if (keymap != null) {
      String[] actionIds = keymap.getActionIds();
      for (String id : actionIds) {
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
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    String[] registeredActionIds = actionManager.getActionIds("");
    for (String id : registeredActionIds) {
      if (actionManager.getAction(id) instanceof ActionGroup) {
        continue;
      }
      if (id.startsWith(QuickList.QUICK_LIST_PREFIX) || addedActions.containsId(id) || result.contains(id)) {
        continue;
      }

      if (keymapManager.getBoundActions().contains(id)) continue;

      result.add(id);
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
    for (String id : result) {
      if (isActionFiltered(filter, actionManager.getAction(id), forceFiltering))
      group.addActionId(id);
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
      for (AnAction child : children) {
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
    for (Object child : group.getChildren()) {
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

  public static Group createMainGroup(final Project project,
                                      final Keymap keymap,
                                      final QuickList[] quickLists,
                                      final String filter,
                                      final boolean forceFiltering) {
    Group mainGroup = new Group(KeyMapBundle.message("all.actions.group.title"), null, null);
    mainGroup.addGroup(createEditorActionsGroup(filter, forceFiltering));
    mainGroup.addGroup(createMainMenuGroup(filter, forceFiltering, false));
    mainGroup.addGroup(createVcsGroup(filter, forceFiltering));
    mainGroup.addGroup(createAntGroup(filter, forceFiltering, project));
    mainGroup.addGroup(createDebuggerActionsGroup(filter, forceFiltering));
    mainGroup.addGroup(createGuiDesignerActionsGroup(filter, forceFiltering, mainGroup));
    mainGroup.addGroup(createBookmarksActionsGroup(filter, forceFiltering));
    mainGroup.addGroup(createExternalToolsGroup(filter, forceFiltering));
    mainGroup.addGroup(createMacrosGroup(filter, forceFiltering));
    mainGroup.addGroup(createQuickListsGroup(filter, forceFiltering, quickLists));

    Group otherGroup = createOtherGroup(filter, forceFiltering, mainGroup, keymap);
    mainGroup.addGroup(otherGroup);

    mainGroup.addGroup(createPluginsActionsGroup(filter, forceFiltering));
    return mainGroup;
  }

  private static boolean isActionFiltered(String filter, AnAction action, boolean force){
    if (filter == null) return true;
    if (action == null) return false;
    final String text = action.getTemplatePresentation().getText();
    if (text != null){
      if (SearchUtil.isComponentHighlighted(text, filter, force, ApplicationManager.getApplication().getComponent(KeymapConfigurable.class))){
        return true;
      }
    }
    final String description = action.getTemplatePresentation().getDescription();
    if (description != null){
      if (SearchUtil.isComponentHighlighted(description, filter, force, ApplicationManager.getApplication().getComponent(KeymapConfigurable.class))){
        return true;
      }
    }
    return false;
  }
}