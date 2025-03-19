package com.intellij.database.datagrid;

import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface GridEditorPanel {
  void apply();

  @NotNull
  JComponent getComponent();

  @NotNull
  EditorEx getEditor();

  @NotNull
  @NlsContexts.PopupContent
  String getInvalidTextErrorMessage();

  void showHistoryPopup();

  @NotNull
  JComponent getGridPreferredFocusedComponent();

  boolean handleError(@NotNull GridRequestSource source, @NotNull ErrorInfo info);

  @NotNull
  String getText();
}
