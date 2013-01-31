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
package com.intellij.ide.util.scopeChooser;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
* User: anna
*/
public abstract class IgnoringComboBox extends JComboBox {
  private int myDirection = 0;

  @Override
  public void processKeyEvent(KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED && e.getModifiers() == 0) {
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        myDirection = -1;
      }
      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        myDirection = 1;
      }
    }
    try {
      super.processKeyEvent(e);
    }
    finally {
      myDirection = 0;
    }
  }

  @Override
  public void setSelectedItem(final Object item) {
    if (!(isIgnored(item))) {
      super.setSelectedItem(item);
    }
  }

  @Override
  public void setSelectedIndex(final int anIndex) {
    int index = anIndex + myDirection;
    final int size = getModel().getSize();
    if (index < 0) {
      index = size - 1;
    } else if (index > size - 1) {
      index = 0;
    }
    Object item = getItemAt(index);
    if (!isIgnored(item)) {
      super.setSelectedIndex(index);
    }
  }

  protected abstract boolean isIgnored(Object item);
}
