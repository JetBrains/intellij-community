/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import javax.swing.*;

/**
* @author nik
*/
abstract class AddItemPopupAction<ItemType> extends ChooseAndAddAction<ItemType> {
  private final String myTitle;
  private final Icon myIcon;
  private final int myIndex;

  public AddItemPopupAction(ClasspathPanel classpathPanel, int index, String title, Icon icon) {
    super(classpathPanel);
    myTitle = title;
    myIcon = icon;
    myIndex = index;
  }

  public String getTitle() {
    return myTitle;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public int getIndex() {
    return myIndex;
  }

}
