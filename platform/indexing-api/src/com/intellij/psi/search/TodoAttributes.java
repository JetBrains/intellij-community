// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public final class TodoAttributes implements Cloneable {
  private static final String ATTRIBUTE_ICON = "icon";
  private static final String ICON_NULL = "null";
  private static final String ICON_DEFAULT = "default";
  private static final String ICON_QUESTION = "question";
  private static final String ICON_IMPORTANT = "important";
  private static final String ELEMENT_OPTION = "option";
  private static final String USE_CUSTOM_COLORS_ATT = "useCustomColors";

  private @Nullable Icon myIcon;
  private TextAttributes myTextAttributes;
  private boolean myShouldUseCustomColors;

  @Internal
  TodoAttributes(@NotNull Element element, @NotNull TextAttributes defaultTodoAttributes) {
    String icon = element.getAttributeValue(ATTRIBUTE_ICON, ICON_NULL);

    switch (icon) {
      case ICON_DEFAULT -> myIcon = IconManager.getInstance().getPlatformIcon(PlatformIcons.TodoDefault);
      case ICON_QUESTION -> myIcon = IconManager.getInstance().getPlatformIcon(PlatformIcons.TodoQuestion);
      case ICON_IMPORTANT -> myIcon = IconManager.getInstance().getPlatformIcon(PlatformIcons.TodoImportant);
      case ICON_NULL -> myIcon = null;
      default -> throw new InvalidDataException(icon);
    }

    myShouldUseCustomColors = Boolean.parseBoolean(element.getAttributeValue(USE_CUSTOM_COLORS_ATT));
    myTextAttributes = myShouldUseCustomColors && element.getChild(ELEMENT_OPTION) != null ? new TextAttributes(element) : defaultTodoAttributes;
  }

  @Internal
  public TodoAttributes(@Nullable Icon icon, @NotNull TextAttributes textAttributes) {
    myIcon = icon;
    myTextAttributes = textAttributes;
  }

  @Internal
  public TodoAttributes(@NotNull TextAttributes textAttributes){
    myTextAttributes = textAttributes;
  }

  public @Nullable Icon getIcon(){
    return myIcon;
  }

  public void setIcon(@Nullable Icon icon) {
    myIcon = icon;
  }

  public @NotNull TextAttributes getTextAttributes() {
    return getCustomizedTextAttributes();
  }

  public @NotNull TextAttributes getCustomizedTextAttributes() {
    return myTextAttributes;
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

  public void writeExternal(@NotNull Element element) {
    String icon = null;
    IconManager iconManager = IconManager.getInstance();
    if (myIcon == iconManager.getPlatformIcon(PlatformIcons.TodoQuestion)) {
      icon = ICON_QUESTION;
    }
    else if (myIcon == iconManager.getPlatformIcon(PlatformIcons.TodoImportant)) {
      icon = ICON_IMPORTANT;
    }
    else if (myIcon == iconManager.getPlatformIcon(PlatformIcons.TodoDefault)) {
      icon = ICON_DEFAULT;
    }
    if (icon != null) {
      element.setAttribute(ATTRIBUTE_ICON, icon);
    }

    if (shouldUseCustomTodoColor()) {
      myTextAttributes.writeExternal(element);
      element.setAttribute(USE_CUSTOM_COLORS_ATT, "true");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TodoAttributes attributes)) return false;
    return myIcon == attributes.myIcon &&
           Objects.equals(myTextAttributes, attributes.myTextAttributes) &&
           myShouldUseCustomColors == attributes.myShouldUseCustomColors;
  }

  @Override
  public int hashCode() {
    int result = myIcon != null ? myIcon.hashCode() : 0;
    result = 29 * result + (myTextAttributes != null ? myTextAttributes.hashCode() : 0);
    result = 29 * result + Boolean.valueOf(myShouldUseCustomColors).hashCode();
    return result;
  }

  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public TodoAttributes clone() {
    var attributes = new TodoAttributes(myIcon, myTextAttributes.clone());
    attributes.myShouldUseCustomColors = myShouldUseCustomColors;
    return attributes;
  }
}
