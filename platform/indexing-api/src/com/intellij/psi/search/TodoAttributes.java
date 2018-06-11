// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Vladimir Kondratyev
 */
public class TodoAttributes implements Cloneable {
  private Icon myIcon;
  private TextAttributes myTextAttributes;
  private boolean myShouldUseCustomColors;

  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String ICON_DEFAULT = "default";
  @NonNls private static final String ICON_QUESTION = "question";
  @NonNls private static final String ICON_IMPORTANT = "important";
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String USE_CUSTOM_COLORS_ATT = "useCustomColors";

  public TodoAttributes(@NotNull Element element, @NotNull TextAttributes defaultTodoAttributes) {
    String icon = element.getAttributeValue(ATTRIBUTE_ICON, ICON_DEFAULT);

    if (ICON_DEFAULT.equals(icon)){
      myIcon = AllIcons.General.TodoDefault;
    }
    else if (ICON_QUESTION.equals(icon)){
      myIcon = AllIcons.General.TodoQuestion;
    }
    else if (ICON_IMPORTANT.equals(icon)){
      myIcon = AllIcons.General.TodoImportant;
    }
    else{
      throw new InvalidDataException(icon);
    }

    myShouldUseCustomColors = Boolean.parseBoolean(element.getAttributeValue(USE_CUSTOM_COLORS_ATT));
    myTextAttributes = myShouldUseCustomColors && element.getChild(ELEMENT_OPTION) != null ? new TextAttributes(element) : defaultTodoAttributes;
  }

  public TodoAttributes(@NotNull Icon icon, @NotNull TextAttributes textAttributes){
    myIcon = icon;
    myTextAttributes = textAttributes;
  }

  public Icon getIcon(){
    return myIcon;
  }

  @NotNull
  public TextAttributes getTextAttributes() {
    return getCustomizedTextAttributes();
  }

  @NotNull
  public TextAttributes getCustomizedTextAttributes() {
    return myTextAttributes;
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  public void writeExternal(@NotNull Element element) {
    String icon = ICON_DEFAULT;
    if (myIcon == AllIcons.General.TodoQuestion) {
      icon = ICON_QUESTION;
    }
    else if (myIcon == AllIcons.General.TodoImportant) {
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
