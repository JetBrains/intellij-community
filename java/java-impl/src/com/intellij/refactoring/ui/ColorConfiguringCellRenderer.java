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
package com.intellij.refactoring.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Oct 18, 2002
 * Time: 5:09:33 PM
 * To change this template use Options | File Templates.
 */
public class ColorConfiguringCellRenderer extends DefaultTableCellRenderer {
  protected void configureColors(boolean isSelected, JTable table, boolean hasFocus, final int row, final int column) {

    if (isSelected) {
      setForeground(table.getSelectionForeground());
    }
    else {
      setForeground(UIUtil.getTableForeground());
    }


    if (hasFocus) {
      setForeground(UIUtil.getTableFocusCellForeground());
    }
  }
}
