/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class JBTextField extends JTextField implements ComponentWithEmptyText {
  private StatusText myEmptyText;

  public JBTextField() {
    init();
  }

  public JBTextField(int i) {
    super(i);
    init();
  }

  public JBTextField(String s) {
    super(s);
    init();
  }

  public JBTextField(String s, int i) {
    super(s, i);
    init();
  }

  private void init() {
    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return JBTextField.this.getText().isEmpty();
      }

      @Override
      protected Rectangle getTextComponentBound() {
        Rectangle bounds = super.getTextComponentBound();
        return new Rectangle(JBTextField.this.getInsets().left, bounds.y, bounds.width, bounds.height);
      }
    };
    myEmptyText.clear();
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myEmptyText.getComponent().setFont(getFont());
    myEmptyText.paint(this, g);
  }
}
