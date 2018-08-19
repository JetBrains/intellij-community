/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationWithSeparator;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.FontUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Default implementation of the {@link ItemPresentation} interface.
 */

public class PresentationData implements ColoredItemPresentation, ComparableObject, LocationPresentation {
  protected final List<PresentableNodeDescriptor.ColoredFragment> myColoredText = ContainerUtil.createLockFreeCopyOnWriteList();

  private Icon myIcon;

  private String myLocationString;
  private String myPresentableText;

  private String myTooltip;
  private TextAttributesKey myAttributesKey;

  private Color myForcedTextForeground;

  private Font myFont;

  private boolean mySeparatorAbove = false;

  private boolean myChanged;
  private String myLocationPrefix;
  private String myLocationSuffix;

  /**
   * Creates an instance with the specified parameters.
   *
   * @param presentableText the name of the object to be presented in most renderers across the program.
   * @param locationString  the location of the object (for example, the package of a class). The location
   *                        string is used by some renderers and usually displayed as grayed text next to
   *                        the item name.
   * @param icon            the icon shown for the node when it is collapsed in a tree, or when it is displayed
   *                        in a non-tree view.
   * @param attributesKey   the attributes for rendering the item text.
   */
  public PresentationData(String presentableText, String locationString, Icon icon,
                          @Nullable TextAttributesKey attributesKey) {
    myIcon = icon;
    myLocationString = locationString;
    myPresentableText = presentableText;
    myAttributesKey = attributesKey;
  }

  /**
   * @deprecated Use {@link #PresentationData(String, String, Icon, TextAttributesKey)} instead.
   */
  @Deprecated
  public PresentationData(String presentableText, String locationString, Icon openIcon, Icon closedIcon,
                          @Nullable TextAttributesKey attributesKey) {
    this(presentableText, locationString, closedIcon, attributesKey);
  }


  /**
   * Creates an instance with no parameters specified.
   */
  public PresentationData() {
  }

  @Override
  public Icon getIcon(boolean open) {
    return myIcon;
  }

  @Nullable
  public Color getForcedTextForeground() {
    return myForcedTextForeground;
  }

  public void setForcedTextForeground(@Nullable Color forcedTextForeground) {
    myForcedTextForeground = forcedTextForeground;
  }

  @Override
  public String getLocationString() {
    return myLocationString;
  }

  @Override
  public String getPresentableText() {
    return myPresentableText;
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
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
   * Sets the name of the object to be presented in most renderers across the program.
   *
   * @param presentableText the name of the object.
   */
  public void setPresentableText(String presentableText) {
    myPresentableText = presentableText;
  }

  /**
   * @param closedIcon the closed icon for the node.
   * @see #setIcons(Icon)
   * @deprecated Different icons for open/closed no longer supported. Use setIcon instead
   *             Sets the icon shown for the node when it is collapsed in a tree, or when it is displayed
   *             in a non-tree view.
   */
  @Deprecated
  public void setClosedIcon(Icon closedIcon) {
    setIcon(closedIcon);
  }


  /**
   * @param openIcon the open icon for the node.
   * @see #setIcons(Icon)
   * @deprecated Different icons for open/closed no longer supported. This function is no op.
   *             Sets the icon shown for the node when it is expanded in the tree.
   */
  @Deprecated
  public void setOpenIcon(Icon openIcon) {
  }

  /**
   * @param icon the icon for the node.
   * @see #setOpenIcon(Icon)
   * @see #setClosedIcon(Icon)
   * @deprecated Different icons for open/closed no longer supported. Use setIcon instead.
   *             Sets both the open and closed icons of the node to the specified icon.
   */

  @Deprecated
  public void setIcons(Icon icon) {
    setIcon(icon);
  }

  /**
   * Copies the presentation parameters from the specified presentation instance.
   *
   * @param presentation the instance to copy the parameters from.
   */
  public void updateFrom(ItemPresentation presentation) {
    setIcon(presentation.getIcon(false));
    setPresentableText(presentation.getPresentableText());
    setLocationString(presentation.getLocationString());
    if (presentation instanceof ColoredItemPresentation) {
      setAttributesKey(((ColoredItemPresentation)presentation).getTextAttributesKey());
    }
    setSeparatorAbove(presentation instanceof ItemPresentationWithSeparator);
    if (presentation instanceof LocationPresentation) {
      myLocationPrefix = ((LocationPresentation)presentation).getLocationPrefix();
      myLocationSuffix = ((LocationPresentation)presentation).getLocationSuffix();
    }
  }

  public boolean hasSeparatorAbove() {
    return mySeparatorAbove;
  }

  public void setSeparatorAbove(final boolean b) {
    mySeparatorAbove = b;
  }

  @Override
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

  public void setTooltip(@Nullable final String tooltip) {
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
    myIcon = null;
    clearText();
    myAttributesKey = null;
    myFont = null;
    myForcedTextForeground = null;
    myLocationString = null;
    myPresentableText = null;
    myTooltip = null;
    myChanged = false;
    mySeparatorAbove = false;
    myLocationSuffix = null;
    myLocationPrefix = null;
  }

  @Override
  @NotNull
  public Object[] getEqualityObjects() {
    return new Object[]{myIcon, myColoredText, myAttributesKey, myFont, myForcedTextForeground, myPresentableText,
      myLocationString, mySeparatorAbove, myLocationPrefix, myLocationSuffix};
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
    myIcon = from.myIcon;
    clearText();
    myColoredText.addAll(from.myColoredText);
    myFont = from.myFont;
    myForcedTextForeground = from.myForcedTextForeground;
    myLocationString = from.myLocationString;
    myPresentableText = from.myPresentableText;
    myTooltip = from.myTooltip;
    mySeparatorAbove = from.mySeparatorAbove;
    myLocationPrefix = from.myLocationPrefix;
    myLocationSuffix = from.myLocationSuffix;
  }

  @Override
  public PresentationData clone() {
    PresentationData clone = new PresentationData();
    clone.copyFrom(this);
    return clone;
  }

  public void applyFrom(PresentationData from) {
    myAttributesKey = getValue(myAttributesKey, from.myAttributesKey);
    myIcon = getValue(myIcon, from.myIcon);

    if (myColoredText.isEmpty()) {
      myColoredText.addAll(from.myColoredText);
    }

    myFont = getValue(myFont, from.myFont);
    myForcedTextForeground = getValue(myForcedTextForeground, from.myForcedTextForeground);
    myLocationString = getValue(myLocationString, from.myLocationString);
    myPresentableText = getValue(myPresentableText, from.myPresentableText);
    myTooltip = getValue(myTooltip, from.myTooltip);
    mySeparatorAbove = mySeparatorAbove || from.mySeparatorAbove;
    myLocationPrefix = getValue(myLocationPrefix, from.myLocationPrefix);
    myLocationSuffix = getValue(myLocationSuffix, from.myLocationSuffix);
  }

  private static <T> T getValue(T ownValue, T fromValue) {
    return ownValue != null ? ownValue : fromValue;
  }

  @Override
  public String getLocationPrefix() {
    return myLocationPrefix == null ? FontUtil.spaceAndThinSpace() : myLocationPrefix;
  }

  @Override
  public String getLocationSuffix() {
    return StringUtil.notNullize(myLocationSuffix, "");
  }
}
