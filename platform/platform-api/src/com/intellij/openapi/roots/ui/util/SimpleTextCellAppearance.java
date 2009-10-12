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
package com.intellij.openapi.roots.ui.util;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SimpleTextCellAppearance implements ModifiableCellAppearance {
  private Icon myIcon;
  private final SimpleTextAttributes myTextAttributes;
  private final String myText;

  public static SimpleTextCellAppearance invalid(String text, Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  public static CellAppearance normal(String text, Icon icon) {
    CompositeAppearance result = CompositeAppearance.single(text);
    result.setIcon(icon);
    return result;
  }

  public SimpleTextCellAppearance(@NotNull String text, Icon icon, SimpleTextAttributes textAttributes) {
    myIcon = icon;
    myTextAttributes = textAttributes;
    myText = text;
  }

  public void customize(SimpleColoredComponent component) {
    component.setIcon(myIcon);
    component.append(myText, myTextAttributes);
  }

  public String getText() {
    return myText;
  }

  public SimpleTextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  public static SimpleTextCellAppearance syntetic(String text, Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }

  public void setIcon(Icon icon) { myIcon = icon; }
}
