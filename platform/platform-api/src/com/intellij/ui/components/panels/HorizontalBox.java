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
package com.intellij.ui.components.panels;

import javax.swing.*;
import java.awt.*;

public class HorizontalBox extends JPanel {

  private final Box myBox;

  public HorizontalBox() {
    setLayout(new BorderLayout());
    myBox = new Box(BoxLayout.X_AXIS) {
      public Component add(Component comp) {
        ((JComponent) comp).setAlignmentY(0f);
        return super.add(comp);
      }
    };
    add(myBox, BorderLayout.CENTER);
    setOpaque(false);
  }

  public Component add(Component aComponent) {
    return myBox.add(aComponent);
  }

  public void remove(Component aComponent) {
    myBox.remove(aComponent);
  }

  public void removeAll() {
    myBox.removeAll();
  }

}
