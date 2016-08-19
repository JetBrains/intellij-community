/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBImageIcon;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

@State(name = "com.intellij.ide.ui.customization.CustomActionsSchema", storages = @Storage("customization.xml"))
public class CustomActionsSchema implements PersistentStateComponent<Element> {
  @NonNls private static final String ACTIONS_SCHEMA = "custom_actions_schema";
  @NonNls private static final String ACTIVE = "active";
  @NonNls private static final String ELEMENT_ACTION = "action";
  @NonNls private static final String ATTRIBUTE_ID = "id";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";

  private final Map<String, String> myIconCustomizations = new HashMap<>();

  private List<ActionUrl> myActions = new ArrayList<>();

  private final Map<String, ActionGroup> myIdToActionGroup = new HashMap<>();

  private final List<Pair> myIdToNameList = new ArrayList<>();

  @NonNls private static final String GROUP = "group";
  private static final Logger LOG = Logger.getInstance(CustomActionsSchema.class);

  private boolean isFirstLoadState = true;

  public CustomActionsSchema() {
    myIdToNameList.add(new Pair(IdeActions.GROUP_MAIN_MENU, ActionsTreeUtil.MAIN_MENU_TITLE));
    myIdToNameList.add(new Pair(IdeActions.GROUP_MAIN_TOOLBAR, ActionsTreeUtil.MAIN_TOOLBAR));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_POPUP, ActionsTreeUtil.EDITOR_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_GUTTER, "Editor Gutter Popup Menu"));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_TAB_POPUP, ActionsTreeUtil.EDITOR_TAB_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionsTreeUtil.PROJECT_VIEW_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_SCOPE_VIEW_POPUP, "Scope View Popup Menu"));
    myIdToNameList.add(new Pair(IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionsTreeUtil.FAVORITES_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_COMMANDER_POPUP, ActionsTreeUtil.COMMANDER_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_J2EE_VIEW_POPUP, ActionsTreeUtil.J2EE_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_NAVBAR_POPUP, "Navigation Bar"));
    myIdToNameList.add(new Pair("NavBarToolBar", "Navigation Bar Toolbar"));

    CustomizableActionGroupProvider.CustomizableActionGroupRegistrar registrar =
      new CustomizableActionGroupProvider.CustomizableActionGroupRegistrar() {
        @Override
        public void addCustomizableActionGroup(@NotNull String groupId, @NotNull String groupTitle) {
          myIdToNameList.add(new Pair(groupId, groupTitle));
        }
      };
    for (CustomizableActionGroupProvider provider : CustomizableActionGroupProvider.EP_NAME.getExtensions()) {
      provider.registerGroups(registrar);
    }
  }

  public static CustomActionsSchema getInstance() {
    return ServiceManager.getService(CustomActionsSchema.class);
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
      final ActionUrl url = new ActionUrl(new ArrayList<>(actionUrl.getGroupPath()), actionUrl.getComponent(),
                                          actionUrl.getActionType(), actionUrl.getAbsolutePosition());
      url.setInitialPosition(actionUrl.getInitialPosition());
      myActions.add(url);
    }
    resortActions();

    myIconCustomizations.putAll(result.myIconCustomizations);
  }

  private void resortActions() {
    Collections.sort(myActions, ActionUrlComparator.INSTANCE);
  }

  public boolean isModified(CustomActionsSchema schema) {
    List<ActionUrl> storedActions = schema.getActions();
    if (ApplicationManager.getApplication().isUnitTestMode() && !storedActions.isEmpty()) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("stored: " + storedActions.toString());
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("actual: " + getActions().toString());
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
  public void loadState(Element element) {
    try {
      DefaultJDOMExternalizer.readExternal(this, element);
    }
    catch (InvalidDataException e) {
      throw new RuntimeException(e);
    }

    Element schElement = element;
    final String activeName = element.getAttributeValue(ACTIVE);
    if (activeName != null) {
      for (Element toolbarElement : element.getChildren(ACTIONS_SCHEMA)) {
        for (Object o : toolbarElement.getChildren("option")) {
          if (Comparing.strEqual(((Element)o).getAttributeValue("name"), "myName") &&
              Comparing.strEqual(((Element)o).getAttributeValue("value"), activeName)) {
            schElement = toolbarElement;
            break;
          }
        }
      }
    }
    for (Object groupElement : schElement.getChildren(GROUP)) {
      ActionUrl url = new ActionUrl();
      try {
        url.readExternal((Element)groupElement);
      }
      catch (InvalidDataException e) {
        throw new RuntimeException(e);
      }
      myActions.add(url);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("read custom actions: " + myActions.toString());
    }

    for (Element action : element.getChildren(ELEMENT_ACTION)) {
      String actionId = action.getAttributeValue(ATTRIBUTE_ID);
      String iconPath = action.getAttributeValue(ATTRIBUTE_ICON);
      if (actionId != null){
        myIconCustomizations.put(actionId, iconPath);
      }
    }

    final boolean reload = !isFirstLoadState;
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

  public static void setCustomizationSchemaForCurrentProjects() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(project);
      if (frame != null) {
        frame.updateView();
      }
    }

    IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(null);
    if (frame != null) {
      frame.updateView();
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    try {
      //noinspection deprecation
      DefaultJDOMExternalizer.writeExternal(this, element);
      for (ActionUrl group : myActions) {
        Element groupElement = new Element(GROUP);
        group.writeExternal(groupElement);
        element.addContent(groupElement);
      }
    }
    catch (WriteExternalException e) {
      throw new RuntimeException(e);
    }
    writeIcons(element);
    return element;
  }

  public AnAction getCorrectedAction(String id) {
    if (! myIdToNameList.contains(new Pair(id, ""))){
      return ActionManager.getInstance().getAction(id);
    }
    if (myIdToActionGroup.get(id) == null) {
      for (Pair pair : myIdToNameList) {
        if (pair.first.equals(id)){
          final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
          if (actionGroup != null) { //J2EE/Commander plugin was disabled
            myIdToActionGroup.put(id, CustomizationUtil.correctActionGroup(actionGroup, this, pair.second, pair.second));
          }
        }
      }
    }
    return myIdToActionGroup.get(id);
  }

  public void fillActionGroups(DefaultMutableTreeNode root){
    final ActionManager actionManager = ActionManager.getInstance();
    for (Pair pair : myIdToNameList) {
      final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(pair.first);
      if (actionGroup != null) { //J2EE/Commander plugin was disabled
        root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createGroup(actionGroup, pair.second, null, null, true, null, false)));
      }
    }
  }


  public boolean isCorrectActionGroup(ActionGroup group, String defaultGroupName) {
    if (myActions.isEmpty()){
      return false;
    }

    final String text = group.getTemplatePresentation().getText();
    if (!StringUtil.isEmpty(text)) {
      for (ActionUrl url : myActions) {
        if (url.getGroupPath().contains(text) || url.getGroupPath().contains(defaultGroupName)) {
          return true;
        }
        if (url.getComponent() instanceof Group) {
          final Group urlGroup = (Group)url.getComponent();
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

  public List<ActionUrl> getChildActions(final ActionUrl url) {
    ArrayList<ActionUrl> result = new ArrayList<>();
    final ArrayList<String> groupPath = url.getGroupPath();
    for (ActionUrl actionUrl : myActions) {
      int index = 0;
      if (groupPath.size() <= actionUrl.getGroupPath().size()){
        while (index < groupPath.size()){
          if (!Comparing.equal(groupPath.get(index), actionUrl.getGroupPath().get(index))){
            break;
          }
          index++;
        }
        if (index == groupPath.size()){
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
    final String path = myIconCustomizations.get(actionId);
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
      final AnAction anAction = actionManager.getAction(actionId);
      if (anAction != null) {
        Icon icon;
        final String iconPath = myIconCustomizations.get(actionId);
        if (iconPath != null && new File(FileUtil.toSystemDependentName(iconPath)).exists()) {
          Image image = null;
          try {
            image = ImageLoader.loadFromStream(VfsUtilCore.convertToURL(VfsUtil.pathToUrl(iconPath)).openStream());
          }
          catch (IOException e) {
            LOG.debug(e);
          }
          icon = image == null ? null : new JBImageIcon(image);
        }
        else {
          icon = AllIcons.Toolbar.Unknown;
        }
        anAction.getTemplatePresentation().setIcon(icon);
        anAction.getTemplatePresentation().setDisabledIcon(IconLoader.getDisabledIcon(icon));
        anAction.setDefaultIcon(false);
      }
    }
    final IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(null);
    if (frame != null) {
      frame.updateView();
    }
  }

  private static class Pair {
    String first;
    String second;

    public Pair(final String first, final String second) {
      this.first = first;
      this.second = second;
    }



    public int hashCode() {
      return first.hashCode();
    }

    public boolean equals(Object obj) {
      return obj instanceof Pair && first.equals(((Pair)obj).first);
    }
  }

  private static class ActionUrlComparator implements Comparator<ActionUrl> {
    public static ActionUrlComparator INSTANCE = new ActionUrlComparator();
    private static final int DELETED = 1;
    @Override
    public int compare(ActionUrl u1, ActionUrl u2) {
      final int w1 = getEquivalenceClass(u1);
      final int w2 = getEquivalenceClass(u2);
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
