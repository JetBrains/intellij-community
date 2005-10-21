package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class QuickList {
  @NonNls public static final String QUICK_LIST_PREFIX = "QuickList.";
  @NonNls public static final String SEPARATOR_ID = QUICK_LIST_PREFIX + "$Separator$";

  @NonNls private static final String ID_TAG = "id";
  @NonNls private static final String READONLY_TAG = "readonly";
  @NonNls private static final String ACTION_TAG = "action";
  @NonNls private static final String DISPLAY_NAME_TAG = "display";
  @NonNls private static final String DESCRIPTION_TAG = "description";


  private String myDisplayName;
  private String myDescription;
  private String[] myActionIds;
  private String[] myDefaultIds;
  private boolean myReadonly;

  /**
   * With read external to be called immediately after in mind
   */
  QuickList() {}

  public QuickList(String displayName, String description, String[] actionIds, boolean isReadonly) {
    this(displayName, description, actionIds, ArrayUtil.EMPTY_STRING_ARRAY, isReadonly);
  }

  public QuickList(String displayName, String description, String[] actionIds, String[] defaultIds, boolean isReadonly) {
    myDisplayName = displayName == null ? "" : displayName;
    myDescription = description == null ? "" : description;
    myActionIds = actionIds;
    myDefaultIds = defaultIds;
    myReadonly = isReadonly;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public boolean isReadonly() {
    return myReadonly;
  }

  public String getDescription() {
    return myDescription;
  }

  public String[] getActionIds() {
    return myActionIds;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QuickList)) return false;

    final QuickList quickList = (QuickList)o;

    if (!Arrays.equals(myActionIds, quickList.myActionIds)) return false;
    if (!myDescription.equals(quickList.myDescription)) return false;
    if (!myDisplayName.equals(quickList.myDisplayName)) return false;

    return true;
  }

  public int hashCode() {
    return 29 * myDisplayName.hashCode() + myDescription.hashCode();

  }

  public String getActionId() {
    return QUICK_LIST_PREFIX + getDisplayName();
  }

  public Map<Keymap, ArrayList<Shortcut>> getShortcutMap(final List<Keymap> keymaps) {
    Map<Keymap, ArrayList<Shortcut>> listShortcuts = new HashMap<Keymap, ArrayList<Shortcut>>();
    String actionId = getActionId();
    for (int i = 0; i < keymaps.size(); i++) {
      Keymap keymap = keymaps.get(i);
      Shortcut[] shortcuts = keymap.getShortcuts(actionId);
      if (shortcuts.length > 0) {
        listShortcuts.put(keymap, new ArrayList<Shortcut>(Arrays.asList(shortcuts)));
      }
    }
    return listShortcuts;
  }

  public void unregisterAllShortcuts(final List<Keymap> keymaps) {
    for (int i = 0; i < keymaps.size(); i++) {
      Keymap keymap = keymaps.get(i);
      keymap.removeAllActionShortcuts(getActionId());
    }
  }

  public void registerShortcuts(Map<Keymap, ArrayList<Shortcut>> shortcutMap, final List<Keymap> keymaps) {
    String actionId = getActionId();
    for (int i = 0; i < keymaps.size(); i++) {
      Keymap keymap = keymaps.get(i);
      ArrayList<Shortcut> shortcuts = shortcutMap.get(keymap);
      if (shortcuts != null) {
        for (int j = 0; j < shortcuts.size(); j++) {
          Shortcut shortcut = shortcuts.get(j);
          keymap.addShortcut(actionId, shortcut);
        }
      }
    }
  }

  public void writeExternal(Element groupElement) {
    groupElement.setAttribute(DISPLAY_NAME_TAG, getDisplayName());
    groupElement.setAttribute(DESCRIPTION_TAG, getDescription());
    groupElement.setAttribute(READONLY_TAG, String.valueOf(isReadonly()));

    String[] actionIds = getActionIds();
    for (int j = 0; j < actionIds.length; j++) {
      String actionId = actionIds[j];
      Element actionElement = new Element(ACTION_TAG);
      actionElement.setAttribute(ID_TAG, actionId);
      groupElement.addContent(actionElement);
    }
  }

  public void readExternal(Element element) {
    myDisplayName = element.getAttributeValue(DISPLAY_NAME_TAG);
    myDescription = element.getAttributeValue(DESCRIPTION_TAG);
    myReadonly = Boolean.valueOf(element.getAttributeValue(READONLY_TAG)).booleanValue();
    List<String> ids = new ArrayList<String>();
    List actions = element.getChildren(ACTION_TAG);
    for (int j = 0; j < actions.size(); j++) {
      Element actionElement = (Element)actions.get(j);
      ids.add(actionElement.getAttributeValue(ID_TAG));
    }
    myActionIds = ids.toArray(new String[ids.size()]);
    myDefaultIds = ArrayUtil.EMPTY_STRING_ARRAY;
  }
}