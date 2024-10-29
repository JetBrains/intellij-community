// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationWithSeparator;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.NlsSafe;
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

public class PresentationData implements ColoredItemPresentation, ComparableObject, LocationPresentation, Cloneable {
  private List<PresentableNodeDescriptor.ColoredFragment> myColoredText = ContainerUtil.createLockFreeCopyOnWriteList();

  private @Nullable Color myBackground;
  private Icon myIcon;

  private @NlsSafe String myLocationString;
  private @NlsSafe String myPresentableText;

  private @Tooltip String myTooltip;
  private TextAttributesKey myAttributesKey;

  private Color myForcedTextForeground;

  private Font myFont;

  private boolean mySeparatorAbove = false;

  private boolean myChanged;
  private @NlsSafe String myLocationPrefix;
  private @NlsSafe String myLocationSuffix;

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
  public PresentationData(@NlsSafe String presentableText, @NlsSafe String locationString, Icon icon,
                          @Nullable TextAttributesKey attributesKey) {
    myIcon = icon;
    myLocationString = locationString;
    myPresentableText = presentableText;
    myAttributesKey = attributesKey;
  }


  /**
   * Creates an instance with no parameters specified.
   */
  public PresentationData() {
  }

  public final @Nullable Color getBackground() {
    return myBackground;
  }

  public final void setBackground(@Nullable Color background) {
    myBackground = background;
  }

  @Override
  public Icon getIcon(boolean open) {
    return myIcon;
  }

  public @Nullable Color getForcedTextForeground() {
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

  public void setLocationString(@NlsSafe String locationString) {
    myLocationString = locationString;
  }

  /**
   * Sets the name of the object to be presented in most renderers across the program.
   *
   * @param presentableText the name of the object.
   */
  public void setPresentableText(@NlsSafe String presentableText) {
    myPresentableText = presentableText;
  }


  /**
   * Copies the presentation parameters from the specified presentation instance.
   *
   * @param presentation the instance to copy the parameters from.
   */
  public void updateFrom(ItemPresentation presentation) {
    if (presentation instanceof PresentationData presentationData) {
      setBackground(presentationData.getBackground());
      for (PresentableNodeDescriptor.ColoredFragment fragment : presentationData.getColoredText()) {
        addText(fragment);
      }
    }
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

  public @Tooltip String getTooltip() {
    return myTooltip;
  }

  public void setTooltip(@Nullable @Tooltip String tooltip) {
    myTooltip = tooltip;
  }

  public boolean isChanged() {
    return myChanged;
  }

  public void setChanged(boolean changed) {
    myChanged = changed;
  }

  public @NotNull List<PresentableNodeDescriptor.ColoredFragment> getColoredText() {
    return myColoredText;
  }

  public void addText(PresentableNodeDescriptor.ColoredFragment coloredFragment) {
    myColoredText.add(coloredFragment);
  }

  public void addText(@NlsContexts.Label String text, SimpleTextAttributes attributes) {
    myColoredText.add(new PresentableNodeDescriptor.ColoredFragment(text, attributes));
  }

  public void clearText() {
    myColoredText.clear();
  }

  public void clear() {
    myBackground = null;
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
  public Object @NotNull [] getEqualityObjects() {
    return new Object[]{myBackground, myIcon, myColoredText, myAttributesKey, myFont, myForcedTextForeground, myPresentableText,
      myLocationString, mySeparatorAbove, myLocationPrefix, myLocationSuffix};
  }

  @Override
  public int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    return ComparableObjectCheck.equals(this, obj);
  }

  public void copyFrom(PresentationData from) {
    if (from == this) {
      return;
    }
    myBackground = from.myBackground;
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
    PresentationData clone;
    try {
      clone = (PresentationData)super.clone();
      clone.myColoredText = ContainerUtil.createLockFreeCopyOnWriteList(myColoredText);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public void applyFrom(PresentationData from) {
    myBackground = getValue(myBackground, from.myBackground);
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
    return StringUtil.notNullize(myLocationSuffix);
  }
}
