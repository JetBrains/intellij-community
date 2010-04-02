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
package com.intellij.openapi.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CheckBoxWithDescription extends JPanel {

  private final JCheckBox myCheckBox;

  public CheckBoxWithDescription(JCheckBox box, @Nullable String description) {
    myCheckBox = box;

    setLayout(new BorderLayout());
    add(myCheckBox, BorderLayout.NORTH);

    if (description != null) {
      final int iconSize = box.getPreferredSize().height;

      final DescriptionLabel desc = new DescriptionLabel(description);
      desc.setBorder(new EmptyBorder(0, iconSize + UIManager.getInt("CheckBox.textIconGap"), 0, 0));
      add(desc, BorderLayout.CENTER);
    }
  }

  public JCheckBox getCheckBox() {
    return myCheckBox;
  }
}
