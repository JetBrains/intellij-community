// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBImageIcon;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@State(name = "com.intellij.ide.ui.customization.CustomActionsSchema", storages = @Storage("customization.xml"), category = SettingsCategory.UI)
public final class CustomActionsSchema implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(CustomActionsSchema.class);

  @NonNls private static final String ACTIONS_SCHEMA = "custom_actions_schema";
  @NonNls private static final String ACTIVE = "active";
  @NonNls private static final String ELEMENT_ACTION = "action";
  @NonNls private static final String ATTRIBUTE_ID = "id";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String GROUP = "group";

  private static final Map<String, String> ourAdditionalIdToName = new ConcurrentHashMap<>();

  private final Map<String, String> myIconCustomizations = new HashMap<>();
  private final Map<String, @Nls String> myIdToName = new LinkedHashMap<>();
  private final Map<String, ActionGroup> myIdToActionGroup = new HashMap<>();
  private final Set<String> myExtGroupIds = new HashSet<>();

  private List<ActionUrl> myActions = new ArrayList<>();
  private boolean isFirstLoadState = true;

  private int myModificationStamp = 0;

  public CustomActionsSchema() {
    myIdToName.put(IdeActions.GROUP_MAIN_MENU, ActionsTreeUtil.getMainMenuTitle());
    if (ToolbarSettings.getInstance().isEnabled()) {
      myIdToName.put(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR, ActionsTreeUtil.getExperimentalToolbar());
    }
    myIdToName.put(IdeActions.GROUP_MAIN_TOOLBAR, ActionsTreeUtil.getMainToolbar());
    myIdToName.put(IdeActions.GROUP_EDITOR_POPUP, ActionsTreeUtil.getEditorPopup());
    myIdToName.put(IdeActions.GROUP_EDITOR_GUTTER, ActionsTreeUtil.getEditorGutterPopupMenu());
    myIdToName.put(IdeActions.GROUP_EDITOR_TAB_POPUP, ActionsTreeUtil.getEditorTabPopup());
    myIdToName.put(IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionsTreeUtil.getProjectViewPopup());
    myIdToName.put(IdeActions.GROUP_SCOPE_VIEW_POPUP, ActionsTreeUtil.getScopeViewPopupMenu());
    myIdToName.put(IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionsTreeUtil.getFavoritesPopup());
    myIdToName.put(IdeActions.GROUP_COMMANDER_POPUP, ActionsTreeUtil.getCommanderPopup());
    myIdToName.put(IdeActions.GROUP_J2EE_VIEW_POPUP, ActionsTreeUtil.getJ2EEPopup());
    myIdToName.put(IdeActions.GROUP_NAVBAR_POPUP, ActionsTreeUtil.getNavigationBarPopupMenu());
    myIdToName.put(IdeActions.GROUP_NAVBAR_TOOLBAR, ActionsTreeUtil.getNavigationBarToolbar());

    fillExtGroups();
    CustomizableActionGroupProvider.EP_NAME.addChangeListener(this::fillExtGroups, null);

    myIdToName.putAll(ourAdditionalIdToName);
  }

  private void fillExtGroups() {
    for (String id : myExtGroupIds) {
      myIdToName.remove(id);
    }
    myExtGroupIds.clear();

    List<Pair<String, @Nls String>> extList = new ArrayList<>();
    CustomizableActionGroupProvider.CustomizableActionGroupRegistrar registrar =
      (groupId, groupTitle) -> {
        extList.add(Pair.create(groupId, groupTitle));
      };
    for (CustomizableActionGroupProvider provider : CustomizableActionGroupProvider.EP_NAME.getExtensions()) {
      provider.registerGroups(registrar);
    }
    extList.sort((o1, o2) -> StringUtil.naturalCompare(o1.second, o2.second));
    for (Pair<String, @Nls String> couple : extList) {
      myExtGroupIds.add(couple.first);
      myIdToName.put(couple.first, couple.second);
    }
  }

  public static void addSettingsGroup(@NotNull String itemId, @Nls @NotNull String itemName) {
    ourAdditionalIdToName.put(itemId, itemName);

    // Need to sync new items with global instance (if it has been created)
    CustomActionsSchema customActionSchema = ApplicationManager.getApplication().getServiceIfCreated(CustomActionsSchema.class);
    if (customActionSchema != null) {
      customActionSchema.myIdToName.put(itemId, itemName);
    }
  }

  public static void removeSettingsGroup(@NotNull String itemId) {
    ourAdditionalIdToName.remove(itemId);

    // Need to sync new items with global instance (if it has been created)
    CustomActionsSchema customActionSchema = ApplicationManager.getApplication().getServiceIfCreated(CustomActionsSchema.class);
    if (customActionSchema != null) {
        customActionSchema.myIdToName.remove(itemId);
    }
  }

  public static CustomActionsSchema getInstance() {
    return ApplicationManager.getApplication().getService(CustomActionsSchema.class);
  }

  public void addAction(ActionUrl url) {
    myActions.add(url);
    resortActions();
  }

  @NotNull
  public List<ActionUrl> getActions() {
    return myActions;
  }

  public void setActions(@NotNull List<ActionUrl> actions) {
    myActions = actions;
    resortActions();
  }

  public void copyFrom(CustomActionsSchema result) {
    myIdToActionGroup.clear();
    myActions.clear();
    myIconCustomizations.clear();

    for (ActionUrl actionUrl : result.myActions) {
      myActions.add(actionUrl.copy());
    }
    resortActions();

    myIconCustomizations.putAll(result.myIconCustomizations);
  }

  private void resortActions() {
    myActions.sort(ActionUrlComparator.INSTANCE);
  }

  public boolean isModified(CustomActionsSchema schema) {
    List<ActionUrl> storedActions = schema.getActions();
    if (ApplicationManager.getApplication().isUnitTestMode() && !storedActions.isEmpty()) {
      LOG.error(IdeBundle.message("custom.action.stored", storedActions));
      LOG.error(IdeBundle.message("custom.action.actual", getActions()));
    }
    if (storedActions.size() != getActions().size()) {
      return true;
    }
    for (int i = 0; i < getActions().size(); i++) {
      if (!getActions().get(i).equals(storedActions.get(i))) {
        return true;
      }
    }
    if (schema.myIconCustomizations.size() != myIconCustomizations.size()) return true;
    for (String actionId : myIconCustomizations.keySet()) {
      if (!Comparing.strEqual(schema.getIconPath(actionId), getIconPath(actionId))) return true;
    }
    return false;
  }

  @Override
  public void loadState(@NotNull Element element) {
    myIdToActionGroup.clear();
    myActions.clear();
    myIconCustomizations.clear();
    DefaultJDOMExternalizer.readExternal(this, element);
    Element schElement = element;
    String activeName = element.getAttributeValue(ACTIVE);
    if (activeName != null) {
      for (Element toolbarElement : element.getChildren(ACTIONS_SCHEMA)) {
        for (Element o : toolbarElement.getChildren("option")) {
          if (Comparing.strEqual(o.getAttributeValue("name"), "myName") &&
              Comparing.strEqual(o.getAttributeValue("value"), activeName)) {
            schElement = toolbarElement;
            break;
          }
        }
      }
    }
    for (Element groupElement : schElement.getChildren(GROUP)) {
      ActionUrl url = new ActionUrl();
      url.readExternal(groupElement);
      myActions.add(url);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(IdeBundle.message("custom.option.testmode", myActions.toString()));
    }

    for (Element action : element.getChildren(ELEMENT_ACTION)) {
      String actionId = action.getAttributeValue(ATTRIBUTE_ID);
      String iconPath = action.getAttributeValue(ATTRIBUTE_ICON);
      if (actionId != null) {
        myIconCustomizations.put(actionId, iconPath);
      }
    }

    boolean reload = !isFirstLoadState;
    if (isFirstLoadState) {
      isFirstLoadState = false;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      initActionIcons();

      if (reload) {
        setCustomizationSchemaForCurrentProjects();
      }
    });
  }

  public void clearFirstLoadState() {
    isFirstLoadState = false;
  }

  public static void setCustomizationSchemaForCurrentProjects() {
    // increment myModificationStamp clear children cache in CustomisedActionGroup
    // as result do it *before* update all toolbars, menu bars and popups
    getInstance().incrementModificationStamp();

    WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      ProjectFrameHelper frame = windowManager.getFrameHelper(project);
      if (frame != null) {
        frame.updateView();
      }
    }

    ProjectFrameHelper frame = windowManager.getFrameHelper(null);
    if (frame != null) {
      frame.updateView();
    }
  }

  public void incrementModificationStamp() {
    myModificationStamp++;
  }

  public int getModificationStamp() {
    return myModificationStamp;
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    //noinspection deprecation
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (ActionUrl group : myActions) {
      Element groupElement = new Element(GROUP);
      group.writeExternal(groupElement);
      element.addContent(groupElement);
    }
    writeIcons(element);
    return element;
  }

  @Nullable
  public AnAction getCorrectedAction(String id) {
    if (!myIdToName.containsKey(id)) {
      return ActionManager.getInstance().getAction(id);
    }

    ActionGroup existing = myIdToActionGroup.get(id);
    if (existing != null) {
      return existing;
    }

    ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
    if (actionGroup != null) { // if a plugin is disabled
      String name = myIdToName.get(id);
      ActionGroup corrected = CustomizationUtil.correctActionGroup(actionGroup, this, name, name, true);
      myIdToActionGroup.put(id, corrected);
      return corrected;
    }
    return null;
  }

  @Nullable
  public String getDisplayName(@NotNull String id) {
    return myIdToName.get(id);
  }

  public void invalidateCustomizedActionGroup(String groupId) {
    ActionGroup group = myIdToActionGroup.get(groupId);
    if (group instanceof CustomisedActionGroup) {
      ((CustomisedActionGroup) group).resetChildren();
    }
  }

  public void fillCorrectedActionGroups(@NotNull DefaultMutableTreeNode root) {
    ActionManager actionManager = ActionManager.getInstance();
    List<String> path = ContainerUtil.newArrayList("root");

    for (Map.Entry<String, @Nls String> entry : myIdToName.entrySet()) {
      ActionGroup actionGroup = (ActionGroup)actionManager.getAction(entry.getKey());
      if (actionGroup != null) {
        root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createCorrectedGroup(actionGroup, entry.getValue(), path, myActions)));
      }
    }
  }

  public void fillActionGroups(DefaultMutableTreeNode root) {
    ActionManager actionManager = ActionManager.getInstance();
    for (String id : myIdToName.keySet()) {
      ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
      if (actionGroup != null) { //J2EE/Commander plugin was disabled
        root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createGroup(actionGroup, myIdToName.get(id), null, null, true, null, false)));
      }
    }
  }

  public boolean isCorrectActionGroup(ActionGroup group, String defaultGroupName) {
    if (myActions.isEmpty()) {
      return false;
    }

    String text = group.getTemplatePresentation().getText();
    if (!StringUtil.isEmpty(text)) {
      for (ActionUrl url : myActions) {
        if (url.getGroupPath().contains(text) || url.getGroupPath().contains(defaultGroupName)) {
          return true;
        }
        if (url.getComponent() instanceof Group) {
          Group urlGroup = (Group)url.getComponent();
          String id = urlGroup.getName() != null ? urlGroup.getName() : urlGroup.getId();
          if (id == null || id.equals(text) || id.equals(defaultGroupName)) {
            return true;
          }
        }
      }
      return false;
    }
    return true;
  }

  @NotNull
  public List<ActionUrl> getChildActions(ActionUrl url) {
    ArrayList<ActionUrl> result = new ArrayList<>();
    ArrayList<String> groupPath = url.getGroupPath();
    for (ActionUrl actionUrl : myActions) {
      int index = 0;
      if (groupPath.size() <= actionUrl.getGroupPath().size()) {
        while (index < groupPath.size()) {
          if (!Objects.equals(groupPath.get(index), actionUrl.getGroupPath().get(index))) {
            break;
          }
          index++;
        }
        if (index == groupPath.size()) {
          result.add(actionUrl);
        }
      }
    }
    return result;
  }

  public void removeIconCustomization(String actionId) {
    myIconCustomizations.remove(actionId);
  }

  public void addIconCustomization(String actionId, String iconPath) {
    myIconCustomizations.put(actionId, iconPath != null ? FileUtil.toSystemIndependentName(iconPath) : null);
  }

  public String getIconPath(String actionId) {
    String path = myIconCustomizations.get(actionId);
    return path == null ? "" : path;
  }

  private void writeIcons(Element parent) {
    for (String actionId : myIconCustomizations.keySet()) {
      Element action = new Element(ELEMENT_ACTION);
      action.setAttribute(ATTRIBUTE_ID, actionId);
      String icon = myIconCustomizations.get(actionId);
      if (icon != null) {
        action.setAttribute(ATTRIBUTE_ICON, icon);
      }
      parent.addContent(action);
    }
  }

  private void initActionIcons() {
    ActionManager actionManager = ActionManager.getInstance();
    for (String actionId : myIconCustomizations.keySet()) {
      AnAction anAction = actionManager.getAction(actionId);
      if (anAction != null) {
        Icon icon = AllIcons.Toolbar.Unknown;
        final String iconPath = myIconCustomizations.get(actionId);
        if (iconPath != null) {
          final File f = new File(FileUtil.toSystemDependentName(iconPath));
          if (f.exists()) {
            Image image = null;
            try {
              image = ImageLoader.loadCustomIcon(f);
            } catch (IOException e) {
              LOG.debug(e);
            }
            if (image != null)
              icon = new JBImageIcon(image);
          }
        }
        anAction.getTemplatePresentation().setIcon(icon);
        anAction.getTemplatePresentation().setDisabledIcon(IconLoader.getDisabledIcon(icon));
        anAction.setDefaultIcon(false);
      }
    }
    ProjectFrameHelper frame = WindowManagerEx.getInstanceEx().getFrameHelper(null);
    if (frame != null) {
      frame.updateView();
    }
  }

  private static final class ActionUrlComparator implements Comparator<ActionUrl> {
    static final ActionUrlComparator INSTANCE = new ActionUrlComparator();
    static int DELETED = 1;

    @Override
    public int compare(ActionUrl u1, ActionUrl u2) {
      int w1 = getEquivalenceClass(u1);
      int w2 = getEquivalenceClass(u2);
      if (w1 != w2) {
        return w1 - w2; // deleted < added < others
      }
      if (w1 == DELETED) {
        return u2.getAbsolutePosition() - u1.getAbsolutePosition(); // within DELETED equivalence class urls with greater position go first
      }
      return u1.getAbsolutePosition() - u2.getAbsolutePosition(); // within ADDED equivalence class: urls with lower position go first
    }

    private static int getEquivalenceClass(ActionUrl url) {
      switch (url.getActionType()) {
        case ActionUrl.DELETED: return 1;
        case ActionUrl.ADDED: return 2;
        default: return 3;
      }
    }
  }
}
