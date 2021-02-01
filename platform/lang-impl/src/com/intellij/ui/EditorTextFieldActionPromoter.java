// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.filterIsInstance;

/**
 * Encapsulates sorting rule that defines what editor actions have precedence to non-editor actions.
 * Current approach is that we want to process text processing-oriented editor actions with higher priority.
 * <p/>
 * Rationale: {@code 'Ctrl+Shift+Right/Left Arrow'} shortcut is bound to
 * {@code 'expand/reduce selection by word'} editor action and {@code 'change dialog width'} non-editor action
 * and we want to use the first one.
 *
 * @author Konstantin Bulenkov
 */
public class EditorTextFieldActionPromoter implements ActionPromoter {
  @Override
  public List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    return filterIsInstance(actions, TextComponentEditorAction.class);
  }
}
