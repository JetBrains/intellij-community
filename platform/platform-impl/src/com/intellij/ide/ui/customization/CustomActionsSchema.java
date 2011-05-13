/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ImageLoader;
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

/**
 * User: anna
 * Date: Jan 20, 2005
 */
public class CustomActionsSchema implements ExportableComponent, NamedJDOMExternalizable {

  @NonNls private static final String ACTIONS_SCHEMA = "custom_actions_schema";
  @NonNls private static final String ACTIVE = "active";
  @NonNls private static final String ELEMENT_ACTION = "action";
  @NonNls private static final String ATTRIBUTE_ID = "id";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";

  private final Map<String, String> myIconCustomizations = new HashMap<String, String>();

  private ArrayList<ActionUrl> myActions = new ArrayList<ActionUrl>();

  private final HashMap<String , ActionGroup> myIdToActionGroup = new HashMap<String, ActionGroup>();

  private static final List<Pair> myIdToNameList = new ArrayList<Pair>();
  static {
    myIdToNameList.add(new Pair(IdeActions.GROUP_MAIN_MENU, ActionsTreeUtil.MAIN_MENU_TITLE));
    myIdToNameList.add(new Pair(IdeActions.GROUP_MAIN_TOOLBAR, ActionsTreeUtil.MAIN_TOOLBAR));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_POPUP, ActionsTreeUtil.EDITOR_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_GUTTER, "Editor Gutter Popup Menu"));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_TAB_POPUP, ActionsTreeUtil.EDITOR_TAB_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionsTreeUtil.PROJECT_VIEW_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionsTreeUtil.FAVORITES_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_COMMANDER_POPUP, ActionsTreeUtil.COMMANDER_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_J2EE_VIEW_POPUP, ActionsTreeUtil.J2EE_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_NAVBAR_POPUP, "Navigation Bar"));
  }

  @NonNls private static final String GROUP = "group";
  private static final Logger LOG = Logger.getInstance("#" + CustomActionsSchema.class.getName());

  public static CustomActionsSchema getInstance() {
    return ServiceManager.getService(CustomActionsSchema.class);
  }

  public void addAction(ActionUrl url) {
    myActions.add(url);
    resortActions();
  }

  public ArrayList<ActionUrl> getActions() {
    return myActions;
  }

  public void setActions(final ArrayList<ActionUrl> actions) {
    myActions = actions;
    resortActions();
  }

  public void copyFrom(CustomActionsSchema result) {
    myIdToActionGroup.clear();
    myActions.clear();
    myIconCustomizations.clear();

    for (ActionUrl actionUrl : result.myActions) {
      final ActionUrl url = new ActionUrl(new ArrayList<String>(actionUrl.getGroupPath()), actionUrl.getComponent(),
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
    final ArrayList<ActionUrl> storedActions = schema.getActions();
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

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    Element schElement = element;
    final String activeName = element.getAttributeValue(ACTIVE);
    if (activeName != null) {
      for (Element toolbarElement : (Iterable<Element>)element.getChildren(ACTIONS_SCHEMA)) {
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
      url.readExternal((Element)groupElement);
      myActions.add(url);
    }
    readIcons(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeActions(element);
    writeIcons(element);
  }

  private void writeActions(Element element) throws WriteExternalException {
    for (ActionUrl group : myActions) {
      Element groupElement = new Element(GROUP);
      group.writeExternal(groupElement);
      element.addContent(groupElement);
    }
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
            myIdToActionGroup.put(id, CustomizationUtil.correctActionGroup(actionGroup, this, pair.second));
          }
        }
      }
    }
    return myIdToActionGroup.get(id);
  }

  public void resetMainActionGroups() {
    for (Pair pair : myIdToNameList) {
      final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(pair.first);
      if (actionGroup != null) { //J2EE/Commander plugin was disabled
        myIdToActionGroup.put(pair.first, CustomizationUtil.correctActionGroup(actionGroup, this, pair.second));
      }
    }
  }

  public static void fillActionGroups(DefaultMutableTreeNode root){
    final ActionManager actionManager = ActionManager.getInstance();
    for (Pair pair : myIdToNameList) {
      final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(pair.first);
      if (actionGroup != null) { //J2EE/Commander plugin was disabled
        root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createGroup(actionGroup, pair.second, null, null, true, null, false)));
      }
    }
  }


  public boolean isCorrectActionGroup(ActionGroup group) {
    if (myActions.isEmpty()){
      return false;
    }
    if (group.getTemplatePresentation() != null &&
        group.getTemplatePresentation().getText() != null) {

      final String text = group.getTemplatePresentation().getText();

      for (ActionUrl url : myActions) {
        if (url.getGroupPath().contains(text)) {
          return true;
        }
        if (url.getComponent() instanceof Group) {
          final Group urlGroup = (Group)url.getComponent();
          String id = urlGroup.getName() != null ? urlGroup.getName() : urlGroup.getId();
          if (id == null || id.equals(text)) {
            return true;
          }
        }
      }
      return false;
    }
    return true;
  }

  public List<ActionUrl> getChildActions(final ActionUrl url) {
    ArrayList<ActionUrl> result = new ArrayList<ActionUrl>();
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

  private void readIcons(Element parent) {
    for (Object actionO : parent.getChildren(ELEMENT_ACTION)) {
      Element action = (Element)actionO;
      final String actionId = action.getAttributeValue(ATTRIBUTE_ID);
      final String iconPath = action.getAttributeValue(ATTRIBUTE_ICON);
      if (actionId != null){
        myIconCustomizations.put(actionId, iconPath);
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        initActionIcons();
      }
    });
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
            image = ImageLoader.loadFromStream(VfsUtil.convertToURL(VfsUtil.pathToUrl(iconPath)).openStream());
          }
          catch (IOException e) {
            LOG.debug(e);
          }
          icon = image != null ? IconLoader.getIcon(image) : null;
        }
        else {
          icon = CustomizableActionsPanel.FULLISH_ICON;
        }
        if (anAction.getTemplatePresentation() != null) {
          anAction.getTemplatePresentation().setIcon(icon);
          anAction.setDefaultIcon(false);
        }
      }
    }
    final IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(null);
    if (frame != null) {
      frame.updateToolbar();
      frame.updateMenuBar();
    }
  }


 @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("title.custom.actions.schemas");
  }

  public String getExternalFileName() {
    return "customization";
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
    private static final int ADDED = 2;
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
