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

import com.intellij.CommonBundle;
import com.intellij.diff.DiffContext;
import com.intellij.diff.tools.util.base.DiffPanelBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SimpleDiffPanel extends DiffPanelBase {
  private static final @NonNls String GOOD_CONTENT = "GoodContent";
  private static final @NonNls String ERROR_CONTENT = "ErrorContent";

  public SimpleDiffPanel(@NotNull JComponent editorPanel, @NotNull DiffContext context) {
    super(context.getProject(), context);
    JPanel centerPanel = JBUI.Panels.simplePanel(editorPanel).addToTop(myNotificationsPanel);

    myContentPanel.add(centerPanel, GOOD_CONTENT);

    setCurrentCard(GOOD_CONTENT, false);
  }

  //
  // Card layout
  //

  public void setGoodContent() {
    setCurrentCard(GOOD_CONTENT);
  }

  public void setErrorContent() {
    setErrorContent(CommonBundle.message("title.error"));
  }

  public void setErrorContent(@NotNull @Nls String message) {
    myContentPanel.add(DiffUtil.createMessagePanel(message), ERROR_CONTENT);
    setCurrentCard(ERROR_CONTENT);
  }

  //
  // Misc
  //

  public boolean isGoodContent() {
    return myCurrentCard.equals(GOOD_CONTENT);
  }
}
