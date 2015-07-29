package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class Utils {
  private Utils() {
  }

  public static void showCompletionPopup(JComponent toolbarComponent,
                                         final JList list,
                                         String title,
                                         final JTextComponent textField,
                                         String ad) {

    final Runnable callback = new Runnable() {
      @Override
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

    if (ad != null) {
      popup.setAdText(ad, SwingConstants.LEFT);
    }

    if (toolbarComponent != null) {
      popup.showUnderneathOf(toolbarComponent);
    }
    else {
      popup.showUnderneathOf(textField);
    }
  }

  public static void setSmallerFont(final JComponent component) {
    if (SystemInfo.isMac) {
      component.setFont(JBUI.Fonts.smallFont());
    }
  }

  public static void setSmallerFontForChildren(JComponent component) {
    for (Component c : component.getComponents()) {
      if (c instanceof JComponent) {
        setSmallerFont((JComponent)c);
      }
    }
  }
}
