// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.UIBundle;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

public final class IOExceptionDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(IOExceptionDialog.class);
  private final JTextArea myErrorLabel;

  public IOExceptionDialog(@NlsContexts.DialogTitle String title, String errorText)  {
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
    Ref<Boolean> ok = new Ref<>(false);
    try {
      ApplicationManager.getApplication().invokeAndWait(() -> {
          IOExceptionDialog dialog = new IOExceptionDialog(title, text);
          dialog.show();
          ok.set(dialog.isOK());
        });
    }
    catch (RuntimeException e) {
      LOG.info(e);
    }

    return ok.get();
  }
}