package com.intellij.codeInsight.generation.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author ven
 */
public class SimpleFieldChooser extends DialogWrapper {
  private final PsiField[] myFields;
  private JList myList;

  public SimpleFieldChooser(PsiField[] members, Project project) {
    super(project, true);
    myFields = members;
    init();
  }

  protected JComponent createCenterPanel() {
    final DefaultListModel model = new DefaultListModel ();
    for (PsiField member : myFields) {
      model.addElement(member);
    }
    myList = new JList(model);
    myList.setCellRenderer(new MyListCellRenderer());
    myList.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            if (myList.getSelectedValues().length > 0) {
              doOKAction();
            }
          }
        }
      }
    );

    myList.setPreferredSize(new Dimension(300, 400));
    return myList;
  }

  public Object[] getSelectedElements() {
    return myList.getSelectedValues();
  }

  private class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Icon icon = null;
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof PsiField) {
        PsiField field = (PsiField)value;
        icon = field.getIcon(0);
        final String text = PsiFormatUtil.formatVariable(field, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
        setText(text);
      }
      super.setIcon(icon);
      return this;
    }
  }
}
