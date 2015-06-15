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

import javax.swing.*;
import java.awt.*;

public class DiffNotifications {
  @NotNull public static final JPanel INSERTED_CONTENT =
    createNotification("Content added", TextDiffType.INSERTED.getColor(null));
  @NotNull public static final JPanel REMOVED_CONTENT =
    createNotification("Content removed", TextDiffType.DELETED.getColor(null));

  @NotNull public static final JPanel EQUAL_CONTENTS =
    createNotification(DiffBundle.message("diff.contents.are.identical.message.text"));
  @NotNull public static final JPanel ERROR =
    createNotification("Can not calculate diff");
  @NotNull public static final JPanel OPERATION_CANCELED =
    createNotification("Can not calculate diff. Operation canceled.");
  @NotNull public static final JPanel DIFF_TOO_BIG =
    createNotification("Can not calculate diff. " + DiffTooBigException.MESSAGE);

  @NotNull
  public static JPanel createNotification(@NotNull String text) {
    return new EditorNotificationPanel().text(text);
  }

  @NotNull
  public static JPanel createNotification(@NotNull String text, @NotNull final Color background) {
    return new EditorNotificationPanel() {
      @Override
      public Color getBackground() {
        return background;
      }
    }.text(text);
  }
}
