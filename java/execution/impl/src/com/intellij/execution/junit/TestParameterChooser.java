// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.util.PopupUtilsKt.getBestPopupPosition;

public abstract class TestParameterChooser<T> {
  protected abstract void collectParameters(@NotNull Consumer<? super List<T>> onCollected);

  protected abstract @NlsSafe @NotNull String getItemText(@NotNull T item);

  protected abstract void runAllParameters();

  protected abstract void runParameter(@NotNull T item);

  public final void choose(@Nullable Editor editor, @NotNull @NlsSafe String presentableName, @NotNull ConfigurationContext context) {
    ListCellRenderer<FirstStep> renderer = new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends FirstStep> list, FirstStep value, int index, boolean selected, boolean hasFocus) {
        setText(value == FirstStep.ALL
                ? ExecutionBundle.message("test.parameter.chooser.run.all")
                : ExecutionBundle.message("test.parameter.chooser.choose"));
      }
    };
    JBPopup popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(List.of(FirstStep.ALL, FirstStep.CHOOSE))
      .setRenderer(renderer)
      .setTitle(ExecutionBundle.message("test.parameter.chooser.step.title", presentableName))
      .setAutoselectOnMouseMove(false)
      .setItemChosenCallback(step -> {
        if (step == FirstStep.ALL) {
          runAllParameters();
        }
        else {
          collectParameters(items -> showItems(editor, presentableName, items, context));
        }
      })
      .setMovable(true)
      .setResizable(false)
      .setRequestFocus(true)
      .setMinSize(JBUI.size(270, 55))
      .createPopup();
    showPopup(popup, editor, context);
  }

  private void showItems(@Nullable Editor editor,
                         @NotNull @NlsSafe String presentableName,
                         @NotNull List<T> items,
                         @NotNull ConfigurationContext context) {
    if (items.size() <= 1) {
      runAllParameters();
      return;
    }
    List<T> withAll = new ArrayList<>(items);
    withAll.addFirst(null); // synthetic "All" entry, rendered via the label below

    @NlsSafe String allLabel = ExecutionBundle.message("test.parameter.chooser.all", items.size());
    ListCellRenderer<T> renderer = new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
        setText(value == null ? allLabel : getItemText(value));
      }
    };

    JBPopup popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(withAll)
      .setRenderer(renderer)
      .setTitle(ExecutionBundle.message("test.parameter.chooser.list.title", presentableName))
      .setAutoselectOnMouseMove(false)
      .setNamerForFiltering(value -> value == null ? "" : getItemText(value))
      .setItemChosenCallback(chosen -> {
        if (chosen == null) {
          runAllParameters();
        }
        else {
          runParameter(chosen);
        }
      })
      .setMovable(true)
      .setResizable(false)
      .setRequestFocus(true)
      .setMinSize(JBUI.size(270, 55))
      .createPopup();
    showPopup(popup, editor, context);
  }

  private static void showPopup(@NotNull JBPopup popup, @Nullable Editor editor, @NotNull ConfigurationContext context) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (popup.isDisposed()) return;
      if (editor != null && !editor.isDisposed()) {
        popup.show(getBestPopupPosition(context.getDataContext()));
      }
      else {
        popup.showInFocusCenter();
      }
    }, ModalityState.any());
  }

  private enum FirstStep {ALL, CHOOSE}
}