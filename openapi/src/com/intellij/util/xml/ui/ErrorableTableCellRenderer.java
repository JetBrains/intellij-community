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

import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.DomElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.lang.annotation.HighlightSeverity;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class ErrorableTableCellRenderer<T extends DomElement> extends DefaultTableCellRenderer {
  private final TableCellRenderer myRenderer;
  private final T myRowDomElement;
  private T myCellValueDomElement;

  final Color myErrorBackground = new Color(255, 204, 204);
  final Color myWarningBackground = new Color(255, 255, 204);


  public ErrorableTableCellRenderer(final T cellValueDomElement, final TableCellRenderer renderer, final T rowDomElement) {
    myCellValueDomElement = cellValueDomElement;
    myRenderer = renderer;
    myRowDomElement = rowDomElement;

  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component = myRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

    final List<DomElementProblemDescriptor> errorProblems =
      DomElementAnnotationsManager.getInstance().getProblems(myCellValueDomElement, true);
    final List<DomElementProblemDescriptor> warningProblems =
      DomElementAnnotationsManager.getInstance().getProblems(myCellValueDomElement, true, true, HighlightSeverity.WARNING);


    final boolean hasErrors = errorProblems.size() > 0;
    if (hasErrors) {
      component.setForeground(Color.RED);
    }
    else {
      component.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
    }

    // highlight empty cell with errors
    if (hasErrors && (value == null || value.toString().trim().length() == 0)) {
      component.setBackground(myErrorBackground);
    }
    else if (warningProblems.size() > 0) {
      component.setBackground(myWarningBackground);
      if(isSelected) component.setForeground(Color.BLACK);
    }

    final List<DomElementProblemDescriptor> errorDescriptors =
      DomElementAnnotationsManager.getInstance().getProblems(myRowDomElement, true, true);

    if (table.getModel().getColumnCount() - 1 == column && errorDescriptors.size() > 0) {
      final JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(component, BorderLayout.CENTER);

      wrapper.setBackground(component.getBackground());

      final JLabel errorLabel = new JLabel(getErrorIcon());

      wrapper.setToolTipText(TooltipUtils.getTooltipText(errorDescriptors));

      wrapper.add(errorLabel, BorderLayout.EAST);

      if (component instanceof JComponent) {
        wrapper.setBorder(((JComponent)component).getBorder());
        ((JComponent)component).setBorder(BorderFactory.createEmptyBorder());
      }


      return wrapper;
    }
    else {
      if (component instanceof JComponent) {
        ((JComponent)component).setToolTipText(errorDescriptors.size() > 0 ? TooltipUtils.getTooltipText(errorDescriptors) : null);
      }
      return component;
    }
  }

  private static Icon getErrorIcon() {
    return IconLoader.getIcon("/general/exclMark.png");
  }
}
