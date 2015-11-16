/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 */
package com.intellij.diff.tools.util;

import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DiffNotifications {
  @Deprecated @NotNull public static final JPanel INSERTED_CONTENT = createInsertedContent();
  @Deprecated @NotNull public static final JPanel REMOVED_CONTENT = createRemovedContent();
  @Deprecated @NotNull public static final JPanel EQUAL_CONTENTS = createEqualContents();
  @Deprecated @NotNull public static final JPanel ERROR = createError();
  @Deprecated @NotNull public static final JPanel OPERATION_CANCELED = createOperationCanceled();
  @Deprecated @NotNull public static final JPanel DIFF_TOO_BIG = createDiffTooBig();

  @NotNull
  public static JPanel createInsertedContent() {
    return createNotification("Content added", TextDiffType.INSERTED.getColor(null));
  }

  @NotNull
  public static JPanel createRemovedContent() {
    return createNotification("Content removed", TextDiffType.DELETED.getColor(null));
  }

  @NotNull
  public static JPanel createEqualContents() {
    return createNotification(DiffBundle.message("diff.contents.are.identical.message.text"));
  }

  @NotNull
  public static JPanel createError() {
    return createNotification("Can not calculate diff");
  }

  @NotNull
  public static JPanel createOperationCanceled() {
    return createNotification("Can not calculate diff. Operation canceled.");
  }

  @NotNull
  public static JPanel createDiffTooBig() {
    return createNotification("Can not calculate diff. " + DiffTooBigException.MESSAGE);
  }

  //
  // Impl
  //

  @NotNull
  public static JPanel createNotification(@NotNull String text) {
    return createNotification(text, null);
  }

  @NotNull
  public static JPanel createNotification(@NotNull String text, @Nullable final Color background) {
    return createNotification(text, background, true);
  }

  @NotNull
  public static JPanel createNotification(@NotNull String text, @Nullable final Color background, boolean showHideAction) {
    final MyEditorNotificationPanel panel = new MyEditorNotificationPanel();
    panel.text(text);
    panel.setBackgroundColor(background);
    if (showHideAction) {
      panel.createActionLabel("Hide", new Runnable() {
        public void run() {
          panel.setVisible(false);
        }
      }).setToolTipText("Hide this notification");
    }
    return panel;
  }

  private static class MyEditorNotificationPanel extends EditorNotificationPanel {
    @Nullable private Color myBackground;

    public void setBackgroundColor(@Nullable Color value) {
      myBackground = value;
    }

    @Override
    @Nullable
    public Color getBackground() {
      return myBackground != null ? myBackground : super.getBackground();
    }
  }
}
