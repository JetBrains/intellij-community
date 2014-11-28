package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.diff.api.DiffTool.DiffContext;
import com.intellij.openapi.util.diff.tools.util.base.DiffPanelBase;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class EditorsDiffPanelBase extends DiffPanelBase {
  private static final String GOOD_CONTENT = "GoodContent";
  private static final String ERROR_CONTENT = "ErrorContent";

  @NotNull private final JComponent myEditorsPanel;

  public EditorsDiffPanelBase(@NotNull JComponent editorPanel,
                              @NotNull DataProvider dataProvider,
                              @NotNull DiffContext context) {
    super(context.getProject(), dataProvider, context);
    myEditorsPanel = editorPanel;

    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(myNotificationsPanel, BorderLayout.NORTH);
    centerPanel.add(myEditorsPanel, BorderLayout.CENTER);

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

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    if (myCurrentCard != GOOD_CONTENT) return null;

    return getCurrentEditor();
  }

  //
  // Abstract
  //

  @Nullable
  protected abstract JComponent getCurrentEditor();
}
