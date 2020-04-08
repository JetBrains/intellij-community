// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.UIBundle;
import com.intellij.util.ObjectUtils;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;

public class IOExceptionDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(IOExceptionDialog.class);
  private final JTextArea myErrorLabel;

  public IOExceptionDialog(String title, String errorText)  {
    super((Project)null, true);
    setTitle(title);
    setOKButtonText(UIBundle.message("io.error.dialog.retry"));

    myErrorLabel = new JTextArea();
    myErrorLabel.setEditable(false);
    myErrorLabel.setText(errorText);
    myErrorLabel.setLineWrap(true);
    myErrorLabel.setWrapStyleWord(true);
    myErrorLabel.setFont(UIManager.getFont("Label.font"));
    myErrorLabel.setBackground(UIManager.getColor("Label.background"));
    myErrorLabel.setForeground(UIManager.getColor("Label.foreground"));

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myErrorLabel;
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    return new Action[] {
      new AbstractAction(UIBundle.message("io.error.dialog.no.proxy")) {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          HttpConfigurable.editConfigurable(ObjectUtils.tryCast(e.getSource(), JComponent.class));
        }
      }
    };
  }

  /**
   * Show the dialog
   * @return {@code true} if "Try Again" button pressed and {@code false} if "Cancel" button pressed
   */
  public static boolean showErrorDialog(@NlsContexts.DialogTitle String title, @NlsContexts.DetailedDescription String text) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(title + ": " + text);
    }
    final Ref<Boolean> ok = Ref.create(false);
    try {
      GuiUtils.runOrInvokeAndWait(() -> {
        IOExceptionDialog dialog = new IOExceptionDialog(title, text);
        dialog.show();
        ok.set(dialog.isOK());
      });
    }
    catch (InterruptedException | InvocationTargetException e) {
      LOG.info(e);
    }

    return ok.get();
  }
}