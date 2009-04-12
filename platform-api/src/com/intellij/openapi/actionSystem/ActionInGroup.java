package com.intellij.openapi.actionSystem;

public class ActionInGroup {

  private DefaultActionGroup myGroup;
  private AnAction myAction;

  ActionInGroup(DefaultActionGroup group, AnAction action) {
    myGroup = group;
    myAction = action;
  }

  public ActionInGroup setAsSecondary(boolean isSecondary) {
    myGroup.setAsPrimary(myAction, !isSecondary);
    return this;
  }

  public ActionGroup getGroup() {
    return myGroup;
  }
}
