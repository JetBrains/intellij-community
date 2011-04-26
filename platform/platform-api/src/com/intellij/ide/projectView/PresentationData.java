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
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationWithSeparator;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of the {@link ItemPresentation} interface.
 */

public class PresentationData implements ItemPresentation, ComparableObject {

  protected final CopyOnWriteArrayList<PresentableNodeDescriptor.ColoredFragment> myColoredText = ContainerUtil.createEmptyCOWList();

  private Icon myClosedIcon;
  private Icon myOpenIcon;

  private String myLocationString;
  private String myPresentableText;

  private String myTooltip;
  private TextAttributesKey myAttributesKey;

  private Color myForcedTextForeground;

  private Font myFont;

  private boolean mySeparatorAbove = false;

  private boolean myChanged;

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
  public PresentationData(String presentableText, String locationString, Icon openIcon, Icon closedIcon,
                          @Nullable TextAttributesKey attributesKey) {
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


  @Nullable
  public Color getForcedTextForeground() {
    return myForcedTextForeground;
  }

  public void setForcedTextForeground(@Nullable Color forcedTextForeground) {
    myForcedTextForeground = forcedTextForeground;
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
    setSeparatorAbove(presentation instanceof ItemPresentationWithSeparator);
  }
  
  public boolean hasSeparatorAbove() {
    return mySeparatorAbove;
  }
  
  public void setSeparatorAbove(final boolean b) {
    mySeparatorAbove = b;
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

  public boolean isChanged() {
    return myChanged;
  }

  public void setChanged(boolean changed) {
    myChanged = changed;
  }

  @NotNull
  public List<PresentableNodeDescriptor.ColoredFragment> getColoredText() {
    return myColoredText;
  }

  public void addText(PresentableNodeDescriptor.ColoredFragment coloredFragment) {
    myColoredText.add(coloredFragment);
  }

  public void addText(String text, SimpleTextAttributes attributes) {
    myColoredText.add(new PresentableNodeDescriptor.ColoredFragment(text, attributes));
  }

  public void clearText() {
    myColoredText.clear();
  }

  public void clear() {
    myOpenIcon = null;
    myClosedIcon = null;
    clearText();
    myAttributesKey = null;
    myFont = null;
    myForcedTextForeground = null;
    myLocationString = null;
    myPresentableText = null;
    myTooltip = null;
    myChanged = false;
    mySeparatorAbove = false;
  }

  public Object[] getEqualityObjects() {
    return new Object[]{myOpenIcon, myClosedIcon, myColoredText, myAttributesKey, myFont, myForcedTextForeground, myPresentableText,
      myLocationString, mySeparatorAbove};
  }

  @Override
  public int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

  @Override
  public boolean equals(Object obj) {
    return ComparableObjectCheck.equals(this, obj);
  }

  public void copyFrom(PresentationData from) {
    if (from == this) {
      return;
    }
    myAttributesKey = from.myAttributesKey;
    myClosedIcon = from.myClosedIcon;
    clearText();
    myColoredText.addAll(from.myColoredText);
    myFont = from.myFont;
    myForcedTextForeground = from.myForcedTextForeground;
    myLocationString = from.myLocationString;
    myOpenIcon = from.myOpenIcon;
    myPresentableText = from.myPresentableText;
    myTooltip = from.myTooltip;
    mySeparatorAbove = from.mySeparatorAbove;
  }

  public PresentationData clone() {
    PresentationData clone = new PresentationData();
    clone.copyFrom(this);
    return clone;
  }

  public void applyFrom(PresentationData from) {
    myAttributesKey = getValue(myAttributesKey, from.myAttributesKey);
    myClosedIcon = getValue(myClosedIcon, from.myClosedIcon);

    if (myColoredText.size() == 0) {
      myColoredText.addAll(from.myColoredText);
    }

    myFont = getValue(myFont, from.myFont);
    myForcedTextForeground = getValue(myForcedTextForeground, from.myForcedTextForeground);
    myLocationString = getValue(myLocationString, from.myLocationString);
    myOpenIcon = getValue(myOpenIcon, from.myOpenIcon);
    myPresentableText = getValue(myPresentableText, from.myPresentableText);
    myTooltip = getValue(myTooltip, from.myTooltip);
    mySeparatorAbove = mySeparatorAbove || from.mySeparatorAbove;
  }

  private <T> T getValue(T ownValue, T fromValue) {
    return ownValue != null ? ownValue : fromValue;
  }
}
