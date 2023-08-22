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
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.NlsContexts.Separator;

import javax.swing.*;

public class ListSeparator {

  private final @Separator String myText;
  private final Icon myIcon;

  public ListSeparator() {
    this("");
  }

  public ListSeparator(@Separator String aText) {
    this(aText, null);
  }

  public ListSeparator(@Separator String name, Icon icon) {
    myText = name;
    myIcon = icon;
  }

  public @Separator String getText() {
    return myText;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
