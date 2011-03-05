package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: zajac
 * Date: 05.03.11
 * Time: 11:09
 * To change this template use File | Settings | File Templates.
 */
public class Utils {
  public static void showCompletionPopup(JComponent toolbarComponent,
                                          final JList list,
                                          String title,
                                          final JTextField textField) {

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
