package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.ui.EditorTextField;

import javax.swing.*;

public class Utils {
  private Utils() {
  }

  public static void showCompletionPopup(JComponent toolbarComponent,
                                          final JList list,
                                          String title,
                                          final EditorTextField textField) {

    final Runnable callback = new Runnable() {
      public void run() {
        String selectedValue = (String)list.getSelectedValue();
        if (selectedValue != null) {
          textField.setText(selectedValue);
        }
      }
    };

    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }

    final JBPopup popup = builder.setMovable(false).setResizable(false)
      .setRequestFocus(true).setItemChoosenCallback(callback).createPopup();

    if (toolbarComponent != null) {
      popup.showUnderneathOf(toolbarComponent);
    }
    else {
      popup.showUnderneathOf(textField);
    }
  }
}
