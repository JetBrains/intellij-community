package com.intellij.debugger.ui.content.newUI;

import org.jdom.Element;

import javax.swing.*;

public class Tab {

  public static final String TAB = "tab";
  private static final String INDEX = "index";
  private static final String NAME = "name";

  private static final String LEFT_SPLIT = "leftSplit";
  private static final String RIGHT_SPLIT = "rightSplit";
  private static final String BOTTOM_SPLIT = "bottomSplit";

  private int myIndex;
  private String myDisplayName;
  private Icon myIcon;

  float myLeftProportion = .2f;
  float myRightProportion = .2f;
  float myBottomProportion = .5f;

  public Tab(Element element) {
    myIndex = Integer.valueOf(element.getAttributeValue(INDEX)).intValue();
    myDisplayName = element.getAttributeValue(NAME);
    setLeftProportion(Float.valueOf(element.getAttributeValue(LEFT_SPLIT)).floatValue());
    setRightProportion(Float.valueOf(element.getAttributeValue(RIGHT_SPLIT)).floatValue());
    setBottomProportion(Float.valueOf(element.getAttributeValue(BOTTOM_SPLIT)).floatValue());
  }

  public Tab(final int index, final String displayName, final Icon icon) {
    myIndex = index;
    myDisplayName = displayName;
    myIcon = icon;
  }

  public int getIndex() {
    return myIndex;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public void write(final Element parentNode) {
    Element tabElement = new Element(TAB);
    tabElement.setAttribute(INDEX, String.valueOf(myIndex));

    if (myDisplayName != null) {
      tabElement.setAttribute(NAME, myDisplayName);
    }

    tabElement.setAttribute(LEFT_SPLIT, String.valueOf(myLeftProportion));
    tabElement.setAttribute(RIGHT_SPLIT, String.valueOf(myRightProportion));
    tabElement.setAttribute(BOTTOM_SPLIT, String.valueOf(myBottomProportion));

    parentNode.addContent(tabElement);
  }

  public float getLeftProportion() {
    return myLeftProportion;
  }

  public void setLeftProportion(final float leftProportion) {
    if (leftProportion <= 0 || leftProportion >= 1.0) return;
    myLeftProportion = leftProportion;
  }

  public float getRightProportion() {
    return myRightProportion;
  }

  public void setRightProportion(final float rightProportion) {
    if (rightProportion <= 0 || rightProportion >= 1.0) return;
    myRightProportion = rightProportion;
  }

  public float getBottomProportion() {
    return myBottomProportion;
  }

  public void setBottomProportion(final float bottomProportion) {
    if (bottomProportion <= 0 || bottomProportion >= 1.0) return;
    myBottomProportion = bottomProportion;
  }
}
