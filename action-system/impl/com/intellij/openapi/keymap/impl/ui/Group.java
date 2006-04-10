package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Mar 18, 2005
 */
public class Group {
  private Group myParent;
  private String myName;
  private String myId;
  private Icon myIcon;
  private Icon myOpenIcon;
  /**
   * Group or action id (String) or Separator or QuickList
   */
  private ArrayList<Object> myChildren;

  private Set<String> myIds = new HashSet<String>();

  public Group(String name, String id, Icon icon, Icon openIcon) {
    myName = name;
    myId = id;
    myIcon = icon;
    myOpenIcon = openIcon;
    myChildren = new ArrayList<Object>();
  }

  public Group(final String name, final Icon openIcon, final Icon icon) {
    myChildren = new ArrayList<Object>();
    myOpenIcon = openIcon;
    myIcon = icon;
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public Icon getOpenIcon() {
    return myOpenIcon;
  }

  public String getId() {
    return myId;
  }

  public void addActionId(String id) {
    myChildren.add(id);
  }

  public void addQuickList(QuickList list) {
    myChildren.add(list);
  }

  public void addGroup(Group group) {
    myChildren.add(group);
    group.myParent = this;
  }

  public void addSeparator() {
    myChildren.add(Separator.getInstance());
  }

  public boolean containsId(String id) {
    return myIds.contains(id);
  }

  public Set<String> initIds(){
    for (Object child : myChildren) {
      if (child instanceof String) {
        myIds.add((String)child);
      }
      else if (child instanceof QuickList) {
        myIds.add(((QuickList)child).getActionId());
      }
      else if (child instanceof Group) {
        myIds.addAll(((Group)child).initIds());
      }
    }
    return myIds;
  }

  public ArrayList<Object> getChildren() {
    return myChildren;
  }

  public int getSize() {
    return myChildren.size();
  }

  public void normalizeSeparators() {
    while (myChildren.size() > 0 && myChildren.get(0) instanceof Separator) {
      myChildren.remove(0);
    }

    while (myChildren.size() > 0 && myChildren.get(myChildren.size() - 1) instanceof Separator) {
      myChildren.remove(myChildren.size() - 1);
    }

    for (int i=1; i < myChildren.size() - 1; i++) {
      if (myChildren.get(i) instanceof Separator && myChildren.get(i + 1) instanceof Separator) {
        myChildren.remove(i);
        i--;
      }
    }
  }

  public String getActionQualifiedPath(String id) {
    for (Object child : myChildren) {
      if (child instanceof QuickList) {
        child = ((QuickList)child).getActionId();
      }
      if (child instanceof String) {
        if (id.equals(child)) {
          AnAction action = ActionManager.getInstance().getActionOrStub(id);
          String path;
          if (action != null) {
            path = action.getTemplatePresentation().getText();
          }
          else {
            path = id;
          }
          return !isRoot() ? getName() + " | " + path : path;
        }
      }
      else if (child instanceof Group) {
        String path = ((Group)child).getActionQualifiedPath(id);
        if (path != null) {
          return !isRoot() ? getName() + " | " + path : path;
        }
      }
    }
    return null;
  }

  public boolean isRoot() {
    return myParent == null;
  }

  public String getQualifiedPath() {
    StringBuffer path = new StringBuffer(64);
    Group group = this;
    while (group != null && !group.isRoot()) {
      path.insert(0, group.getName() + " | ");
      group = group.myParent;
    }
    return path.toString();
  }

  public void addAll(Group group) {
    for (Object o : group.getChildren()) {
      if (o instanceof String) {
        addActionId((String)o);
      }
      else if (o instanceof QuickList) {
        addQuickList((QuickList)o);
      }
      else if (o instanceof Group) {
        addGroup((Group)o);
      }
      else if (o instanceof Separator) {
        addSeparator();
      }
    }
  }

  public ActionGroup constructActionGroup(final boolean popup){
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup(getName(), popup);
    AnAction groupToRestorePresentation = null;
    if (getName() != null){
      groupToRestorePresentation = actionManager.getAction(getName());
    } else {
      if (getId() != null){
        groupToRestorePresentation = actionManager.getAction(getId());
      }
    }
    if (groupToRestorePresentation != null){
      group.copyFrom(groupToRestorePresentation);
    }
    for (Object o : myChildren) {
      if (o instanceof String) {
        group.add(actionManager.getAction((String)o));
      }
      else if (o instanceof Separator) {
        group.addSeparator();
      }
      else if (o instanceof Group) {
        group.add(((Group)o).constructActionGroup(popup));
      }
    }
    return group;
  }


  public boolean equals(Object object) {
    if (!(object instanceof Group)) return false;
    final Group group = ((Group)object);
    if (group.getName() != null && getName() != null){
      return group.getName().equals(getName());
    }
    if (getChildren() != null && group.getChildren() != null){
      if (getChildren().size() != group.getChildren().size()){
        return false;
      }

      for (int i = 0; i < getChildren().size(); i++) {
        if (!getChildren().get(i).equals(group.getChildren().get(i))){
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public int hashCode() {
    return getName() != null ? getName().hashCode() : 0;
  }

  public String toString() {
    return getName();
  }
}
