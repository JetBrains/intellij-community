/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
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

  public TodoAttributes(@NotNull Element element, @NotNull TextAttributes defaultTodoAttributes) throws InvalidDataException {
    String icon = element.getAttributeValue(ATTRIBUTE_ICON,ICON_DEFAULT);

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
    if (element.getChild(ELEMENT_OPTION) == null) {
      myTextAttributes = defaultTodoAttributes;
    }
    else {
      myTextAttributes = new TextAttributes(element);
    }

    // default color setting
    final String useCustomColors = element.getAttributeValue(USE_CUSTOM_COLORS_ATT);
    myShouldUseCustomColors = useCustomColors != null && Boolean.valueOf(useCustomColors).booleanValue();
  }

  TodoAttributes(@NotNull Icon icon, @NotNull TextAttributes textAttributes){
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

  public void writeExternal(Element element) throws WriteExternalException {
    String icon;
    if (myIcon == AllIcons.General.TodoDefault){
      icon = ICON_DEFAULT;
    }
    else if (myIcon == AllIcons.General.TodoQuestion){
      icon = ICON_QUESTION;
    }
    else if (myIcon == AllIcons.General.TodoImportant){
      icon = ICON_IMPORTANT;
    }
    else{
      throw new WriteExternalException("");
    }
    element.setAttribute(ATTRIBUTE_ICON, icon);
    myTextAttributes.writeExternal(element);

    // default color setting
    element.setAttribute(USE_CUSTOM_COLORS_ATT, Boolean.toString(shouldUseCustomTodoColor()));
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
