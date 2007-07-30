/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide.projectView;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import javax.swing.*;

/**
 * Default implementation of the {@link ItemPresentation} interface.
 */

public class PresentationData implements ItemPresentation {
  private Icon myClosedIcon;
  private Icon myOpenIcon;
  private String myLocationString;
  private String myPresentableText;
  private String myTooltip;
  private TextAttributesKey myAttributesKey;

  /**
   * Creates an instance with the specified parameters.
   *
   * @param presentableText the name of the object to be presented in most renderers across the program.
   * @param locationString  the location of the object (for example, the package of a class). The location
   *                        string is used by some renderers and usually displayed as grayed text next to
   *                        the item name.
   * @param openIcon        the icon shown for the node when it is expanded in the tree.
   * @param closedIcon      the icon shown for the node when it is collapsed in a tree, or when it is displayed
   *                        in a non-tree view.
   * @param attributesKey   the attributes for rendering the item text.
   */
  public PresentationData(String presentableText, String locationString, Icon openIcon, Icon closedIcon,TextAttributesKey attributesKey) {
    myClosedIcon = closedIcon;
    myLocationString = locationString;
    myOpenIcon = openIcon;
    myPresentableText = presentableText;
    myAttributesKey = attributesKey;
  }

  /**
   * Creates an instance with no parameters specified.
   */
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

  /**
   * Sets the icon shown for the node when it is collapsed in a tree, or when it is displayed
   * in a non-tree view.
   *
   * @param closedIcon the closed icon for the node.
   * @see #setIcons(javax.swing.Icon)
   */

  public void setClosedIcon(Icon closedIcon) {
    myClosedIcon = closedIcon;
  }

  /**
   * Sets the location of the object (for example, the package of a class). The location
   * string is used by some renderers and usually displayed as grayed text next to the item name.
   *
   * @param locationString the location of the object.
   */

  public void setLocationString(String locationString) {
    myLocationString = locationString;
  }

  /**
   * Sets the icon shown for the node when it is expanded in the tree.
   *
   * @param openIcon the open icon for the node.
   * @see #setIcons(javax.swing.Icon)
   */

  public void setOpenIcon(Icon openIcon) {
    myOpenIcon = openIcon;
  }

  /**
   * Sets the name of the object to be presented in most renderers across the program.
   *
   * @param presentableText the name of the object.
   */
  public void setPresentableText(String presentableText) {
    myPresentableText = presentableText;
  }

  /**
   * Sets both the open and closed icons of the node to the specified icon.
   *
   * @param icon the icon for the node.
   * @see #setOpenIcon(javax.swing.Icon)
   * @see #setClosedIcon(javax.swing.Icon)
   */

  public void setIcons(Icon icon) {
    setClosedIcon(icon);
    setOpenIcon(icon);
  }

  /**
   * Copies the presentation parameters from the specified presentation instance.
   *
   * @param presentation the instance to copy the parameters from.
   */
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

  /**
   * Sets the attributes for rendering the item text.
   *
   * @param attributesKey the attributes for rendering the item text.
   */
  public void setAttributesKey(final TextAttributesKey attributesKey) {
    myAttributesKey = attributesKey;
  }

  public String getTooltip() {
    return myTooltip;
  }

  public void setTooltip(final String tooltip) {
    myTooltip = tooltip;
  }
}
