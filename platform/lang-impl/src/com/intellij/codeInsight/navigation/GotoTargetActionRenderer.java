// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import javax.swing.*;
import java.awt.*;

/**
 * @deprecated Use GotoTargetRendererNew instead
 */
@Deprecated
final class GotoTargetActionRenderer extends DefaultListCellRenderer {

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (value != null) {
      GotoTargetHandler.AdditionalAction action = (GotoTargetHandler.AdditionalAction)value;
      setText(action.getText());
      setIcon(action.getIcon());
    }
    return result;
  }
}
