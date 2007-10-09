/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public class TodoAttributes implements JDOMExternalizable, Cloneable {
  public static final Icon DEFAULT_ICON = IconLoader.getIcon("/general/todoDefault.png");
  public static final Icon QUESTION_ICON = IconLoader.getIcon("/general/todoQuestion.png");
  public static final Icon IMPORTANT_ICON = IconLoader.getIcon("/general/todoImportant.png");

  private Icon myIcon;
  private TextAttributes myTextAttributes = new TextAttributes();
  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String ICON_DEFAULT = "default";
  @NonNls private static final String ICON_QUESTION = "question";
  @NonNls private static final String ICON_IMPORTANT = "important";
  @NonNls private static final String ELEMENT_OPTION = "option";

  public TodoAttributes() {
  }

  private TodoAttributes(Icon icon, TextAttributes textAttributes){
    myIcon = icon;
    myTextAttributes = textAttributes;
  }

  public Icon getIcon(){
    return myIcon;
  }

  public TextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  public static TodoAttributes createDefault() {
    TextAttributes textAttributes = createDefaultTextAttributes();
    return new TodoAttributes(DEFAULT_ICON, textAttributes);
  }

  private static TextAttributes createDefaultTextAttributes() {
    TextAttributes textAttributes = new TextAttributes(Color.blue, null,null,null,Font.BOLD | Font.ITALIC);
    textAttributes.setErrorStripeColor(Color.blue);
    return textAttributes;
  }

  public void readExternal(Element element) throws InvalidDataException {
    String icon = element.getAttributeValue(ATTRIBUTE_ICON,ICON_DEFAULT);

    if (ICON_DEFAULT.equals(icon)){
      myIcon = DEFAULT_ICON;
    }
    else if (ICON_QUESTION.equals(icon)){
      myIcon = QUESTION_ICON;
    }
    else if (ICON_IMPORTANT.equals(icon)){
      myIcon = IMPORTANT_ICON;
    }
    else{
      throw new InvalidDataException(icon);
    }
    myTextAttributes.readExternal(element);
    if (element.getChild(ELEMENT_OPTION) == null) {
      myTextAttributes = createDefaultTextAttributes();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    String icon;
    if (myIcon == DEFAULT_ICON){
      icon = ICON_DEFAULT;
    }
    else if (myIcon == QUESTION_ICON){
      icon = ICON_QUESTION;
    }
    else if (myIcon == IMPORTANT_ICON){
      icon = ICON_IMPORTANT;
    }
    else{
      throw new WriteExternalException("");
    }
    element.setAttribute(ATTRIBUTE_ICON, icon);
    myTextAttributes.writeExternal(element);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TodoAttributes)) return false;

    final TodoAttributes attributes = (TodoAttributes)o;

    if (myIcon != attributes.myIcon) return false;
    if (myTextAttributes != null ? !myTextAttributes.equals(attributes.myTextAttributes) : attributes.myTextAttributes != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myIcon != null ? myIcon.hashCode() : 0;
    result = 29 * result + (myTextAttributes != null ? myTextAttributes.hashCode() : 0);
    return result;
  }

  public TodoAttributes clone() {
    try {
      TextAttributes textAttributes = myTextAttributes.clone();
      TodoAttributes attributes = (TodoAttributes)super.clone();
      attributes.myTextAttributes = textAttributes;
      return attributes;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }
}
