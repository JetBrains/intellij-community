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
package com.intellij.diff.tools.fragmented;

import com.intellij.CommonBundle;
import com.intellij.diff.DiffContext;
import com.intellij.diff.tools.util.base.DiffPanelBase;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.diff.util.DiffUtil.createMessagePanel;

public class UnifiedDiffPanel extends DiffPanelBase {
  private static final @NonNls String GOOD_CONTENT = "GoodContent";
  private static final @NonNls String LOADING_CONTENT = "LoadingContent";
  private static final @NonNls String TOO_BIG_CONTENT = "TooBigContent";
  private static final @NonNls String OPERATION_CANCELED_CONTENT = "OperationCanceledContent";
  private static final @NonNls String ERROR_CONTENT = "ErrorContent";

  @NotNull private final AsyncProcessIcon.Big myBusyIcon;

  public UnifiedDiffPanel(@Nullable Project project, @NotNull JComponent content, @NotNull DiffContext context) {
    super(project, context);
    myBusyIcon = new AsyncProcessIcon.Big("UnifiedDiff");
    JPanel centerPanel = JBUI.Panels.simplePanel(content).addToTop(myNotificationsPanel);
    myContentPanel.add(centerPanel, GOOD_CONTENT);
    myContentPanel.add(myBusyIcon, LOADING_CONTENT);
    myContentPanel.add(createMessagePanel(DiffBundle.message("error.can.not.calculate.diff.file.too.big")), TOO_BIG_CONTENT);
    myContentPanel.add(createMessagePanel(DiffBundle.message("error.can.not.calculate.diff.operation.canceled")), OPERATION_CANCELED_CONTENT);
    myContentPanel.add(createMessagePanel(CommonBundle.message("title.error")), ERROR_CONTENT);

    setCurrentCard(LOADING_CONTENT, false);
  }

  //
  // Card layout
  //

  public void setLoadingContent() {
    setCurrentCard(LOADING_CONTENT);
  }

  public void setGoodContent() {
    setCurrentCard(GOOD_CONTENT);
  }

  public void setTooBigContent() {
    setCurrentCard(TOO_BIG_CONTENT);
  }

  public void setOperationCanceledContent() {
    setCurrentCard(OPERATION_CANCELED_CONTENT);
  }

  public void setErrorContent() {
    setCurrentCard(ERROR_CONTENT);
  }

  @Override
  protected void setCurrentCard(@NotNull String card) {
    if (card.equals(LOADING_CONTENT)) {
      myBusyIcon.resume();
    }
    else {
      myBusyIcon.suspend();
    }

    super.setCurrentCard(card);
  }

  //
  // Misc
  //

  public boolean isGoodContent() {
    return myCurrentCard.equals(GOOD_CONTENT);
  }
}
