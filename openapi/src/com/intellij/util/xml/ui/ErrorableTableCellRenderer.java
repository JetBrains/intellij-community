/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util.xml.ui;

import com.intellij.util.xml.highlighting.DomElementProblemDescription;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.DomElement;
import com.intellij.openapi.util.IconLoader;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.*;
import java.awt.*;

public class ErrorableTableCellRenderer<T extends DomElement> extends DefaultTableCellRenderer {
  private final TableCellRenderer myRenderer;
  private final T myRowDomElement;
  private T myCellValueDomElement;


  public ErrorableTableCellRenderer(final T cellValueDomElement, final TableCellRenderer renderer, final T rowDomElement) {
    myCellValueDomElement = cellValueDomElement;
    myRenderer = renderer;
    myRowDomElement = rowDomElement;

  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component = myRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

    final java.util.List<DomElementProblemDescription> problems =
      DomElementAnnotationsManager.getInstance().getProblems(myCellValueDomElement, true);

    final boolean hasErrors = problems.size() > 0;
    if (hasErrors) {
      component.setForeground(Color.RED);
    }
    else {
      component.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
    }

    if (hasErrors && (value == null || value.toString().trim().length() == 0)) {
      // empty cell with errors
      component.setBackground(new Color(255, 204, 204));
    }

    final java.util.List<DomElementProblemDescription> problemDescriptions =
      DomElementAnnotationsManager.getInstance().getProblems(myRowDomElement, true, true);

    if (table.getModel().getColumnCount() - 1 == column && problemDescriptions.size() > 0) {
      final JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(component, BorderLayout.CENTER);

      wrapper.setBackground(component.getBackground());

      final JLabel errorLabel = new JLabel(getErrorIcon());

      ToolTipManager.sharedInstance().registerComponent(errorLabel);

      wrapper.setToolTipText(TooltipUtils.getTooltipText(problemDescriptions));

      wrapper.add(errorLabel, BorderLayout.EAST);

      if (component instanceof JComponent) {
        wrapper.setBorder(((JComponent)component).getBorder());
        ((JComponent)component).setBorder(BorderFactory.createEmptyBorder());
      }


      return wrapper;
    }
    else {
      if (component instanceof JComponent) {
        ((JComponent)component).setToolTipText(hasErrors ? TooltipUtils.getTooltipText(problemDescriptions) : null);
      }

      return component;
    }
  }

  private static Icon getErrorIcon() {
    return IconLoader.getIcon("/general/exclMark.png");
  }
}
