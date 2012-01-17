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
package com.intellij.ui;

import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import java.awt.*;

public class CommandButtonGroup extends JPanel {
  public static final int LEFT = 1;
  public static final int RIGHT = 2;
  private int myPreferredH = 0;
  private int myPreferredW = 0;
  @JdkConstants.BoxLayoutAxis private final int myAxis;

  public CommandButtonGroup() {
    this(BoxLayout.X_AXIS);
  }

  /**
   * Creates new <code>CommandButtonGroup</code> panel with specified orientation.
   * @param axis possible values of this parameter are defined in <code>BoxLayout</code>
   * @see javax.swing.BoxLayout#X_AXIS
   * @see javax.swing.BoxLayout#Y_AXIS
   */
  public CommandButtonGroup(@JdkConstants.BoxLayoutAxis int axis) {
    myAxis = axis;
    setLayout(new BoxLayout(this, axis));
    //setBorder(new EmptyBorder(5, 5, 5, 5));
    if (axis == BoxLayout.X_AXIS){
      add(Box.createHorizontalGlue());
    }
    else{
      //add(Box.createVerticalGlue());
    }
  }

  public void addButton(AbstractButton button) {
    addButton(button, RIGHT);
  }

  public void addButton(AbstractButton button, int position) {
    if (LEFT == position){
      add(button, 0);
      if (myAxis == BoxLayout.X_AXIS){
        add(Box.createHorizontalStrut(5), 1);
      }
      else{
        add(Box.createVerticalStrut(5), 1);
      }
    }
    else{
      if (myAxis == BoxLayout.X_AXIS){
        add(Box.createHorizontalStrut(5));
      }
      else{
        add(Box.createVerticalStrut(5));
      }
      add(button);
    }
    Dimension prefSize = button.getPreferredSize();
    if (prefSize.height > myPreferredH){
      myPreferredH = prefSize.height;
    }
    if (prefSize.width > myPreferredW){
      myPreferredW = prefSize.width;
    }
    updateButtonSizes();
  }

  private void updateButtonSizes() {
    Dimension dim = new Dimension(myPreferredW, myPreferredH);
    Component[] components = getComponents();
    if (components == null) return;
    for(int i = 0; i < components.length; i++){
      if (components[i] instanceof AbstractButton){
        AbstractButton button = (AbstractButton)components[i];
        button.setPreferredSize(dim);
        button.setMaximumSize(dim);
        button.setMinimumSize(dim);
      }
    }
  }
}
