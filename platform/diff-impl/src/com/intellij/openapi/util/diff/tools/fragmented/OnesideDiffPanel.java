package com.intellij.openapi.util.diff.tools.fragmented;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.comparison.DiffTooBigException;
import com.intellij.openapi.util.diff.tools.util.base.DiffPanelBase;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.util.diff.util.DiffUtil.createMessagePanel;

class OnesideDiffPanel extends DiffPanelBase {
  private static final String GOOD_CONTENT = "GoodContent";
  private static final String LOADING_CONTENT = "LoadingContent";
  private static final String TOO_BIG_CONTENT = "TooBigContent";
  private static final String OPERATION_CANCELED_CONTENT = "OperationCanceledContent";
  private static final String ERROR_CONTENT = "ErrorContent";

  @NotNull private final Editor myEditor;

  @NotNull private final JPanel myEditorPanel;
  @NotNull private final AsyncProcessIcon.Big myBusyIcon;

  public OnesideDiffPanel(@Nullable Project project,
                          @NotNull OnesideContentPanel content,
                          @NotNull Editor editor,
                          @NotNull DataProvider provider,
                          @NotNull DiffContext context) {
    super(project, provider, context);
    myEditor = editor;

    myEditorPanel = content;
    myBusyIcon = new AsyncProcessIcon.Big("OnesideDiff");

    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(myNotificationsPanel, BorderLayout.NORTH);
    centerPanel.add(myEditorPanel, BorderLayout.CENTER);

    myContentPanel.add(centerPanel, GOOD_CONTENT);
    myContentPanel.add(myBusyIcon, LOADING_CONTENT);
    myContentPanel.add(createMessagePanel("Can not calculate diff. " + DiffTooBigException.MESSAGE), TOO_BIG_CONTENT);
    myContentPanel.add(createMessagePanel("Can not calculate diff. Operation canceled."), OPERATION_CANCELED_CONTENT);
    myContentPanel.add(createMessagePanel("Error"), ERROR_CONTENT);

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
    if (card == LOADING_CONTENT) {
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

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myCurrentCard == GOOD_CONTENT ? myEditor.getContentComponent() : null;
  }
}
