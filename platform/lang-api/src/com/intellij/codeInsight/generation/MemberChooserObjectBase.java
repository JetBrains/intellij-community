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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
*/
public class MemberChooserObjectBase implements MemberChooserObject {
  private final String myText;
  private final Icon myIcon;

  public MemberChooserObjectBase(@Nullable final String text) {
    this(text, null);
  }

  public MemberChooserObjectBase(@Nullable final String text, @Nullable final Icon icon) {
    myText = StringUtil.notNullize(text);
    myIcon = icon;
  }

  @Override
  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, getText(), getTextAttributes(tree), false, component);
    component.setIcon(myIcon);
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  protected SimpleTextAttributes getTextAttributes(JTree tree) {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, tree.getForeground());
  }

}
