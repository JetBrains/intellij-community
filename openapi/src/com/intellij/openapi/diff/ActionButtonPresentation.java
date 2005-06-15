package com.intellij.openapi.diff;

public class ActionButtonPresentation {
  private final boolean myIsEnabled;
  private final boolean myCloseDialog;
  private final String myName;

  public static ActionButtonPresentation createApplyButton(){
    return new ActionButtonPresentation(true, "&Apply", true);
  }

  public ActionButtonPresentation(final boolean isEnabled, final String name, final boolean closeDialog) {
    myIsEnabled = isEnabled;
    myName = name;
    myCloseDialog = closeDialog;
  }

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public String getName() {
    return myName;
  }

  public boolean closeDialog() {
    return myCloseDialog;
  }

  public void run(DiffViewer diffViewer){

  }
}
