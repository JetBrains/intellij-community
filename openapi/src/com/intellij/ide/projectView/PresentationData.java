package com.intellij.ide.projectView;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import javax.swing.*;

public class PresentationData implements ItemPresentation{
  private Icon myClosedIcon;
  private Icon myOpenIcon;
  private String myLocationString;
  private String myPresentableText;
  private TextAttributesKey myAttributesKey;

  public PresentationData(String presentableText, String locationString, Icon openIcon, Icon closedIcon,TextAttributesKey attributesKey) {
    myClosedIcon = closedIcon;
    myLocationString = locationString;
    myOpenIcon = openIcon;
    myPresentableText = presentableText;
    myAttributesKey = attributesKey;
  }

  public PresentationData() {
  }

  public Icon getIcon(boolean open) {
    return open ? myOpenIcon : myClosedIcon;
  }

  public String getLocationString() {
    return myLocationString;
  }

  public String getPresentableText() {
    return myPresentableText;
  }

  public void setClosedIcon(Icon closedIcon) {
    myClosedIcon = closedIcon;
  }

  public void setLocationString(String locationString) {
    myLocationString = locationString;
  }

  public void setOpenIcon(Icon openIcon) {
    myOpenIcon = openIcon;
  }

  public void setPresentableText(String presentableText) {
    myPresentableText = presentableText;
  }

  public void setIcons(Icon icon) {
    setClosedIcon(icon);
    setOpenIcon(icon);
  }

  public void updateFrom(ItemPresentation presentation) {
    setClosedIcon(presentation.getIcon(false));
    setOpenIcon(presentation.getIcon(true));
    setPresentableText(presentation.getPresentableText());
    setLocationString(presentation.getLocationString());
    setAttributesKey(presentation.getTextAttributesKey());
  }

  public TextAttributesKey getTextAttributesKey() {
    return myAttributesKey;
  }

  public void setAttributesKey(final TextAttributesKey attributesKey) {
    myAttributesKey = attributesKey;
  }
}
