// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class TodoAttributes implements Cloneable {
  private Icon myIcon;
  private TextAttributes myTextAttributes;
  private boolean myShouldUseCustomColors;

  private static final @NonNls String ATTRIBUTE_ICON = "icon";
  private static final @NonNls String ICON_DEFAULT = "default";
  private static final @NonNls String ICON_QUESTION = "question";
  private static final @NonNls String ICON_IMPORTANT = "important";
  private static final @NonNls String ELEMENT_OPTION = "option";
  private static final @NonNls String USE_CUSTOM_COLORS_ATT = "useCustomColors";

  public TodoAttributes(@NotNull Element element, @NotNull TextAttributes defaultTodoAttributes) {
    String icon = element.getAttributeValue(ATTRIBUTE_ICON, ICON_DEFAULT);

    IconManager iconManager = IconManager.getInstance();
    if (ICON_DEFAULT.equals(icon)){
      myIcon = iconManager.getPlatformIcon(PlatformIcons.TodoDefault);
    }
    else if (ICON_QUESTION.equals(icon)){
      myIcon = iconManager.getPlatformIcon(PlatformIcons.TodoQuestion);
    }
    else if (ICON_IMPORTANT.equals(icon)){
      myIcon = iconManager.getPlatformIcon(PlatformIcons.TodoImportant);
    }
    else{
      throw new InvalidDataException(icon);
    }

    myShouldUseCustomColors = Boolean.parseBoolean(element.getAttributeValue(USE_CUSTOM_COLORS_ATT));
    myTextAttributes = myShouldUseCustomColors && element.getChild(ELEMENT_OPTION) != null ? new TextAttributes(element) : defaultTodoAttributes;
  }

  public TodoAttributes(@NotNull Icon icon, @NotNull TextAttributes textAttributes) {
    myIcon = icon;
    myTextAttributes = textAttributes;
  }

  public TodoAttributes(@NotNull TextAttributes textAttributes){
    myTextAttributes = textAttributes;
  }

  public @NotNull Icon getIcon(){
    return ObjectUtils.chooseNotNull(myIcon, IconManager.getInstance().getPlatformIcon(PlatformIcons.TodoDefault));
  }

  public @NotNull TextAttributes getTextAttributes() {
    return getCustomizedTextAttributes();
  }

  public @NotNull TextAttributes getCustomizedTextAttributes() {
    return myTextAttributes;
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  public void writeExternal(@NotNull Element element) {
    String icon = ICON_DEFAULT;
    IconManager iconManager = IconManager.getInstance();
    if (myIcon == iconManager.getPlatformIcon(PlatformIcons.TodoQuestion)) {
      icon = ICON_QUESTION;
    }
    else if (myIcon == iconManager.getPlatformIcon(PlatformIcons.TodoImportant)) {
      icon = ICON_IMPORTANT;
    }

    if (!icon.equals(ICON_DEFAULT)) {
      element.setAttribute(ATTRIBUTE_ICON, icon);
    }

    if (shouldUseCustomTodoColor()) {
      myTextAttributes.writeExternal(element);
      element.setAttribute(USE_CUSTOM_COLORS_ATT, "true");
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TodoAttributes)) return false;

    final TodoAttributes attributes = (TodoAttributes)o;

    return myIcon == attributes.myIcon &&
           !(myTextAttributes != null ? !myTextAttributes.equals(attributes.myTextAttributes) : attributes.myTextAttributes != null) &&
           myShouldUseCustomColors == attributes.myShouldUseCustomColors;
  }

  public int hashCode() {
    int result = myIcon != null ? myIcon.hashCode() : 0;
    result = 29 * result + (myTextAttributes != null ? myTextAttributes.hashCode() : 0);
    result = 29 * result + Boolean.valueOf(myShouldUseCustomColors).hashCode();
    return result;
  }

  public boolean shouldUseCustomTodoColor() {
    return myShouldUseCustomColors;
  }

  public void setUseCustomTodoColor(boolean useCustomColors, @NotNull TextAttributes defaultTodoAttributes) {
    myShouldUseCustomColors = useCustomColors;
    if (!useCustomColors) {
      myTextAttributes = defaultTodoAttributes;
    }
  }

  @Override
  public TodoAttributes clone() {
    try {
      TextAttributes textAttributes = myTextAttributes.clone();
      TodoAttributes attributes = (TodoAttributes)super.clone();
      attributes.myTextAttributes = textAttributes;
      attributes.myShouldUseCustomColors = myShouldUseCustomColors;
      return attributes;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }
}
