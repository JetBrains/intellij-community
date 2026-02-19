// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware;
import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

public class PushTargetTextField extends TextFieldWithCompletion {
  public PushTargetTextField(@NotNull Project project, @NotNull List<String> targetVariants, @NotNull String defaultTargetName) {
    super(project, getCompletionProvider(targetVariants), defaultTargetName, true, true, true);
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        selectAll();
      }

      @Override
      public void focusLost(FocusEvent e) {
        removeSelection();
      }
    });
  }

  @Override
  protected void updateBorder(final @NotNull EditorEx editor) {
  }

  private static @NotNull TextCompletionProvider getCompletionProvider(final @NotNull List<String> targetVariants) {
    return new ValuesCompletionProviderDumbAware<>(new DefaultTextCompletionValueDescriptor.StringValueDescriptor() {
      @Override
      public int compare(String item1, String item2) {
        return 0; // keep 'targetVariants' order intact
      }
    }, targetVariants);
  }
}
