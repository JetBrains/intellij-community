package com.intellij.ui;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupUpdateProcessor;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.Collection;

/**
 * @author pegov
 */
public abstract class JBListWithHintProvider extends JBList {
  private JBPopup myHint;

  public JBListWithHintProvider() {
    addSelectionListener();
  }

  public JBListWithHintProvider(ListModel dataModel) {
    super(dataModel);
    addSelectionListener();
  }

  public JBListWithHintProvider(Object... listData) {
    super(listData);
    addSelectionListener();
  }

  public JBListWithHintProvider(Collection items) {
    super(items);
    addSelectionListener();
  }
  
  private void addSelectionListener() {
    addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        if (getClientProperty(PopupChooserBuilder.SELECTED_BY_MOUSE_EVENT) != Boolean.TRUE) {
          final Object[] selectedValues = ((JList)e.getSource()).getSelectedValues();
          if (selectedValues.length != 1) return;

          final PsiElement element = getPsiElementForHint(selectedValues[0]);
          if (element != null && element.isValid()) {
            updateHint(element);
          }
        }
      }
    });
  }

  protected abstract PsiElement getPsiElementForHint(final Object selectedValue);

  public void registerHint(final JBPopup hint) {
    hideHint();
    myHint = hint;
  }
  
  public void hideHint() {
    if (myHint != null && myHint.isVisible()) {
      myHint.cancel();
    }

    myHint = null;
  }
  
  public void updateHint(PsiElement element) {
    if (myHint == null || !myHint.isVisible()) return;

    final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
    if (updateProcessor != null) {
      myHint.cancel();
      updateProcessor.updatePopup(element);
    }
  }
  
}
