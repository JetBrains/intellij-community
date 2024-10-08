// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ChooseOneOrAllRunnable<T extends PsiElement> implements Runnable {
  private final T[] myClasses;
  private final Editor myEditor;

  private final @NlsContexts.PopupTitle String myTitle;

  public ChooseOneOrAllRunnable(@NotNull List<? extends T> classes, @NotNull Editor editor, @NotNull @NlsContexts.PopupTitle String title, @NotNull Class<T> type) {
    myClasses = ArrayUtil.toObjectArray(classes, type);
    myEditor = editor;
    myTitle = title;
  }

  protected abstract void selected(T @NotNull ... classes);

  @Override
  public void run() {
    if (myClasses.length == 1) {
      selected((T[])ArrayUtil.toObjectArray(myClasses[0].getClass(), myClasses[0]));
    }
    else if (myClasses.length > 0) {
      PsiElementListCellRenderer<T> renderer = createRenderer();

      Arrays.sort(myClasses, renderer.getComparator());

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        selected(myClasses);
        return;
      }
      List<Object> model = new ArrayList<>(Arrays.asList(myClasses));
      String selectAll = CodeInsightBundle.message("highlight.thrown.exceptions.chooser.all.entry");
      model.add(0, selectAll);

      IPopupChooserBuilder<Object> builder = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(model)
        .setRenderer(renderer) // exploit PsiElementListCellRenderer ability to render strings too
        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        .setItemChosenCallback(selectedValue -> {
          if (selectedValue.equals(selectAll)) {
            selected(myClasses);
          }
          else {
            selected((T[])ArrayUtil.toObjectArray(selectedValue.getClass(), selectedValue));
          }
        })
        .setTitle(myTitle);
      renderer.installSpeedSearch(builder);

      ApplicationManager.getApplication().invokeLater(() -> builder
        .createPopup()
        .showInBestPositionFor(myEditor));
    }
  }

  protected abstract @NotNull PsiElementListCellRenderer<T> createRenderer();
}
