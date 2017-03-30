/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.intellij;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJSpinnerBorder extends MacComboBoxBorder {
  @Override boolean isFocused(Component c) {
    if (c.hasFocus()) return true;

    if (c instanceof JSpinner) {
      JSpinner spinner = (JSpinner)c;
      if (spinner.getEditor() != null) {
        synchronized (spinner.getEditor().getTreeLock()) {
          return  spinner.getEditor().getComponent(0).hasFocus();
        }
      }
    }
    return false;
  }

  @Override Area getButtonBounds(Component c) {
    Rectangle bounds = null;
    if (c instanceof JSpinner && ((JSpinner)c).getUI() instanceof MacIntelliJSpinnerUI) {
      MacIntelliJSpinnerUI ui = (MacIntelliJSpinnerUI)((JSpinner)c).getUI();
      bounds = ui.getArrowButtonBounds();
    }
    return bounds != null ? new Area(bounds) : new Area();
  }

  @Override boolean isRound(Component c) {
    return true;
  }
}
