// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.util.ImageLoader;
import com.intellij.util.ObjectUtils;
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

@State(
  name = "com.intellij.ide.ui.customization.CustomActionsSchema",
  storages = @Storage(value = "customization.xml", usePathMacroManager = false),
  category = SettingsCategory.UI
)
public final class CustomActionsSchema implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(CustomActionsSchema.class);
  /**
   * Original icon should be saved in template presentation when one customizes action icon
   */
  @NonNls public static final Key<Icon> PROP_ORIGINAL_ICON = Key.create("originalIcon");

  @NonNls private static final String ACTIONS_SCHEMA = "custom_actions_schema";
  @NonNls private static final String ACTIVE = "active";
  @NonNls private static final String ELEMENT_ACTION = "action";
  @NonNls private static final String ATTRIBUTE_ID = "id";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String GROUP = "group";

  private static final Map<String, String> additionalIdToName = new ConcurrentHashMap<>();

  private final Map<String, String> iconCustomizations = new HashMap<>();
  private final Map<String, @Nls String> idToName = new LinkedHashMap<>();
  private final Map<String, ActionGroup> idToActionGroup = new HashMap<>();
  private final Set<String> extGroupIds = new HashSet<>();

  private final List<ActionUrl> actions = new ArrayList<>();
  private boolean isFirstLoadState = true;

  private int modificationStamp = 0;

  public CustomActionsSchema() {
    idToName.put(IdeActions.GROUP_MAIN_MENU, ActionsTreeUtil.getMainMenuTitle());
    if (ToolbarSettings.getInstance().isEnabled()) {
      idToName.put(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR, ActionsTreeUtil.getExperimentalToolbar());
    }
    idToName.put(IdeActions.GROUP_MAIN_TOOLBAR, ActionsTreeUtil.getMainToolbar());
    idToName.put(IdeActions.GROUP_EDITOR_POPUP, ActionsTreeUtil.getEditorPopup());
    idToName.put(IdeActions.GROUP_EDITOR_GUTTER, ActionsTreeUtil.getEditorGutterPopupMenu());
    idToName.put(IdeActions.GROUP_EDITOR_TAB_POPUP, ActionsTreeUtil.getEditorTabPopup());
    idToName.put(IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionsTreeUtil.getProjectViewPopup());
    idToName.put(IdeActions.GROUP_SCOPE_VIEW_POPUP, ActionsTreeUtil.getScopeViewPopupMenu());
    idToName.put(IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionsTreeUtil.getFavoritesPopup());
    idToName.put(IdeActions.GROUP_COMMANDER_POPUP, ActionsTreeUtil.getCommanderPopup());
    idToName.put(IdeActions.GROUP_J2EE_VIEW_POPUP, ActionsTreeUtil.getJ2EEPopup());
    idToName.put(IdeActions.GROUP_NAVBAR_POPUP, ActionsTreeUtil.getNavigationBarPopupMenu());
    idToName.put(IdeActions.GROUP_NAVBAR_TOOLBAR, ActionsTreeUtil.getNavigationBarToolbar());

    fillExtGroups();
    CustomizableActionGroupProvider.EP_NAME.addChangeListener(this::fillExtGroups, null);

    idToName.putAll(additionalIdToName);
  }

  private void fillExtGroups() {
    for (String id : extGroupIds) {
      idToName.remove(id);
    }
    extGroupIds.clear();

    List<Pair<String, @Nls String>> extList = new ArrayList<>();
    CustomizableActionGroupProvider.CustomizableActionGroupRegistrar registrar = (groupId, groupTitle) -> {
      extList.add(Pair.create(groupId, groupTitle));
    };
    for (CustomizableActionGroupProvider provider : CustomizableActionGroupProvider.EP_NAME.getExtensionList()) {
      provider.registerGroups(registrar);
    }
    extList.sort((o1, o2) -> NaturalComparator.INSTANCE.compare(o1.second, o2.second));
    for (Pair<String, @Nls String> couple : extList) {
      extGroupIds.add(couple.first);
      idToName.put(couple.first, couple.second);
    }
  }

  public static void addSettingsGroup(@NotNull String itemId, @Nls @NotNull String itemName) {
    additionalIdToName.put(itemId, itemName);

    // Need to sync new items with global instance (if it has been created)
    CustomActionsSchema customActionSchema = ApplicationManager.getApplication().getServiceIfCreated(CustomActionsSchema.class);
    if (customActionSchema != null) {
      customActionSchema.idToName.put(itemId, itemName);
    }
  }

  public static void removeSettingsGroup(@NotNull String itemId) {
    additionalIdToName.remove(itemId);

    // Need to sync new items with global instance (if it has been created)
    CustomActionsSchema customActionSchema = ApplicationManager.getApplication().getServiceIfCreated(CustomActionsSchema.class);
    if (customActionSchema != null) {
        customActionSchema.idToName.remove(itemId);
    }
  }

  public static CustomActionsSchema getInstance() {
    return ApplicationManager.getApplication().getService(CustomActionsSchema.class);
  }

  public void addAction(@NotNull ActionUrl url) {
    if (!actions.contains(url) && !actions.remove(url.getInverted())) {
      actions.add(url);
    }
  }

  /**
   * Mutable list is returned.
   */
  public @NotNull List<ActionUrl> getActions() {
    return actions;
  }

  public void setActions(@NotNull List<ActionUrl> newActions) {
    assert actions != newActions;
    actions.clear();
    actions.addAll(newActions);
    actions.sort(ActionUrlComparator.INSTANCE);
  }

  public void copyFrom(CustomActionsSchema result) {
    idToActionGroup.clear();
    actions.clear();
    Set<String> ids = new HashSet<>(iconCustomizations.keySet());
    iconCustomizations.clear();

    for (ActionUrl actionUrl : result.actions) {
      addAction(actionUrl.copy());
    }
    actions.sort(ActionUrlComparator.INSTANCE);

    iconCustomizations.putAll(result.iconCustomizations);
    ids.forEach(id -> {
      iconCustomizations.putIfAbsent(id, null);
    });
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
    if (schema.iconCustomizations.size() != iconCustomizations.size()) return true;
    for (String actionId : iconCustomizations.keySet()) {
      if (!Comparing.strEqual(schema.getIconPath(actionId), getIconPath(actionId))) return true;
    }
    return false;
  }

  @Override
  public void loadState(@NotNull Element element) {
    idToActionGroup.clear();
    actions.clear();
    iconCustomizations.clear();
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
      addAction(url);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(IdeBundle.message("custom.option.testmode", actions.toString()));
    }

    for (Element action : element.getChildren(ELEMENT_ACTION)) {
      String actionId = action.getAttributeValue(ATTRIBUTE_ID);
      String iconPath = action.getAttributeValue(ATTRIBUTE_ICON);
      if (actionId != null) {
        iconCustomizations.put(actionId, iconPath);
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
    modificationStamp++;
  }

  public int getModificationStamp() {
    return modificationStamp;
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    //noinspection deprecation
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (ActionUrl group : actions) {
      Element groupElement = new Element(GROUP);
      group.writeExternal(groupElement);
      element.addContent(groupElement);
    }
    writeIcons(element);
    return element;
  }

  @Nullable
  public AnAction getCorrectedAction(String id) {
    if (!idToName.containsKey(id)) {
      return ActionManager.getInstance().getAction(id);
    }

    ActionGroup existing = idToActionGroup.get(id);
    if (existing != null) {
      return existing;
    }

    ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
    if (actionGroup != null) { // if a plugin is disabled
      String name = idToName.get(id);
      ActionGroup corrected = CustomizationUtil.correctActionGroup(actionGroup, this, name, name, true);
      idToActionGroup.put(id, corrected);
      return corrected;
    }
    return null;
  }

  @Nullable
  public String getDisplayName(@NotNull String id) {
    return idToName.get(id);
  }

  public void invalidateCustomizedActionGroup(String groupId) {
    ActionGroup group = idToActionGroup.get(groupId);
    if (group instanceof CustomisedActionGroup) {
      ((CustomisedActionGroup) group).resetChildren();
    }
  }

  public void fillCorrectedActionGroups(@NotNull DefaultMutableTreeNode root) {
    ActionManager actionManager = ActionManager.getInstance();
    List<String> path = ContainerUtil.newArrayList("root");

    for (Map.Entry<String, @Nls String> entry : idToName.entrySet()) {
      ActionGroup actionGroup = (ActionGroup)actionManager.getAction(entry.getKey());
      if (actionGroup != null) {
        root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createCorrectedGroup(actionGroup, entry.getValue(), path, actions)));
      }
    }
  }

  public void fillActionGroups(DefaultMutableTreeNode root) {
    ActionManager actionManager = ActionManager.getInstance();
    for (String id : idToName.keySet()) {
      ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
      if (actionGroup != null) { //J2EE/Commander plugin was disabled
        root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createGroup(actionGroup, idToName.get(id), null, null, true, null, false)));
      }
    }
  }

  public boolean isCorrectActionGroup(ActionGroup group, String defaultGroupName) {
    if (actions.isEmpty()) {
      return false;
    }

    String text = group.getTemplatePresentation().getText();
    if (!StringUtil.isEmpty(text)) {
      for (ActionUrl url : actions) {
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
    for (ActionUrl actionUrl : actions) {
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
    iconCustomizations.put(actionId, null);
  }

  public void addIconCustomization(String actionId, String iconPath) {
    iconCustomizations.put(actionId, iconPath != null ? FileUtil.toSystemIndependentName(iconPath) : null);
  }

  public String getIconPath(String actionId) {
    String path = iconCustomizations.get(actionId);
    return path == null ? "" : path;
  }

  private void writeIcons(Element parent) {
    for (String actionId : iconCustomizations.keySet()) {
      Element action = new Element(ELEMENT_ACTION);
      action.setAttribute(ATTRIBUTE_ID, actionId);
      String icon = iconCustomizations.get(actionId);
      if (icon != null) {
        action.setAttribute(ATTRIBUTE_ICON, icon);
      }
      parent.addContent(action);
    }
  }

  void initActionIcons() {
    ActionManager actionManager = ActionManager.getInstance();
    for (String actionId : iconCustomizations.keySet()) {
      AnAction anAction = actionManager.getAction(actionId);
      if (anAction != null) {
        Icon icon = null;
        final String iconPath = iconCustomizations.get(actionId);
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
        Presentation template = anAction.getTemplatePresentation();
        if (template.getClientProperty(PROP_ORIGINAL_ICON) == null && anAction.isDefaultIcon()) {
          ObjectUtils.consumeIfNotNull(template.getIcon(), i -> template.putClientProperty(PROP_ORIGINAL_ICON, i));
        }
        if (icon == null && iconCustomizations.containsKey(actionId)) {
          icon = template.getClientProperty(PROP_ORIGINAL_ICON);
        }
        template.setIcon(icon);
        template.setDisabledIcon(icon != null ? IconLoader.getDisabledIcon(icon) : null);
        anAction.setDefaultIcon(iconPath == null);
        PresentationFactory.updatePresentation(anAction);
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
