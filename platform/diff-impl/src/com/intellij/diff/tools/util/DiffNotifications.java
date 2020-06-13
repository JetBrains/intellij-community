// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util;

import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class DiffNotifications {
  @NotNull
  public static JPanel createInsertedContent() {
    return createNotification(DiffBundle.message("notification.status.content.added"), TextDiffType.INSERTED.getColor(null));
  }

  @NotNull
  public static JPanel createRemovedContent() {
    return createNotification(DiffBundle.message("notification.status.content.removed"), TextDiffType.DELETED.getColor(null));
  }

  @NotNull
  public static JPanel createEqualContents() {
    return createEqualContents(true, true);
  }

  @NotNull
  public static JPanel createEqualContents(boolean equalCharsets, boolean equalSeparators) {
    if (!equalCharsets && !equalSeparators) {
      return createNotification(DiffBundle.message("diff.contents.have.differences.only.in.charset.and.line.separators.message.text"));
    }
    if (!equalSeparators) {
      return createNotification(DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text"));
    }
    if (!equalCharsets) {
      return createNotification(DiffBundle.message("diff.contents.have.differences.only.in.charset.message.text"));
    }
    return createNotification(DiffBundle.message("diff.contents.are.identical.message.text"));
  }

  @NotNull
  public static JPanel createError() {
    return createNotification(DiffBundle.message("diff.cant.calculate.diff"));
  }

  @NotNull
  public static JPanel createOperationCanceled() {
    return createNotification(DiffBundle.message("error.can.not.calculate.diff.operation.canceled"));
  }

  @NotNull
  public static JPanel createDiffTooBig() {
    return createNotification(DiffBundle.message("error.can.not.calculate.diff.file.too.big"));
  }

  //
  // Impl
  //

  @NotNull
  public static JPanel createNotification(@NotNull @Nls String text) {
    return createNotification(text, null);
  }

  @NotNull
  public static JPanel createNotification(@NotNull @Nls String text, @Nullable final Color background) {
    return createNotification(text, background, true);
  }

  @NotNull
  public static JPanel createNotification(@NotNull @Nls String text, @Nullable final Color background, boolean showHideAction) {
    final EditorNotificationPanel panel = new EditorNotificationPanel(background);
    panel.text(text);
    if (showHideAction) {
      HyperlinkLabel link = panel.createActionLabel(DiffBundle.message("button.hide.notification"), () -> panel.setVisible(false));
      link.setToolTipText(DiffBundle.message("hide.this.notification"));
    }
    return panel;
  }
}
