package com.intellij.ide.util.treeView.smartTree;

import javax.swing.*;

public class ActionPresentationData implements ActionPresentation{
  private final String myText;
  private final String myDescription;
  private final Icon myIcon;

  public ActionPresentationData(String text, String description, Icon icon) {
    myText = text;
    myDescription = description;
    myIcon = icon;
  }

  public String getText() {
    return myText;
  }

  public String getDescription() {
    return myDescription;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
