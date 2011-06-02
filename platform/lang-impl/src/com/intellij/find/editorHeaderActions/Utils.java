package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class Utils {
  private Utils() {
  }

  public static void showCompletionPopup(JComponent toolbarComponent,
                                          final JList list,
                                          String title,
                                          final JTextComponent textField) {

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

  public static void setSmallerFont(final JComponent component) {
    if (SystemInfo.isMac) {
      Font f = new JLabel(" ").getFont();
      Font font = smaller(f);
      component.setFont(font);
    }
  }

  static Font smaller(Font f) {
    return f.deriveFont(f.getStyle(), f.getSize() - 2);
  }

  public static void setSmallerFontForChildren(JComponent component) {
    for (Component c : component.getComponents()) {
      if (c instanceof JComponent) {
        setSmallerFont((JComponent)c);
      }
    }
  }

  public static boolean ensureOkToWrite(Editor e) {
    final PsiFile psiFile = PsiDocumentManager.getInstance(e.getProject()).getPsiFile(e.getDocument());
    boolean okWritable;
    if (psiFile != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        okWritable = ReadonlyStatusHandler.ensureFilesWritable(e.getProject(), virtualFile);
      } else {
        okWritable = psiFile.isWritable();
      }
    } else  {
      okWritable = e.getDocument().isWritable();
    }
    return okWritable;
  }
}
