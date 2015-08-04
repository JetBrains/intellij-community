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

import com.intellij.diff.DiffContext;
import com.intellij.diff.tools.util.base.DiffPanelBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SimpleDiffPanel extends DiffPanelBase {
  private static final String GOOD_CONTENT = "GoodContent";
  private static final String ERROR_CONTENT = "ErrorContent";

  public SimpleDiffPanel(@NotNull JComponent editorPanel,
                         @NotNull DataProvider dataProvider,
                         @NotNull DiffContext context) {
    super(context.getProject(), dataProvider, context);
    JPanel centerPanel = JBUI.Panels.simplePanel(editorPanel).addToTop(myNotificationsPanel);

    myContentPanel.add(centerPanel, GOOD_CONTENT);
    myContentPanel.add(DiffUtil.createMessagePanel("Error"), ERROR_CONTENT);

    setCurrentCard(GOOD_CONTENT, false);
  }

  //
  // Card layout
  //

  public void setGoodContent() {
    setCurrentCard(GOOD_CONTENT);
  }

  public void setErrorContent() {
    setCurrentCard(ERROR_CONTENT);
  }

  //
  // Misc
  //

  public boolean isGoodContent() {
    return myCurrentCard == GOOD_CONTENT;
  }
}
