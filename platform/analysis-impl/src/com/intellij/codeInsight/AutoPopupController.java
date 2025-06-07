// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AutoPopupController {
  /**
   * Settings this user data key to the editor with a completion provider
   * makes the autopopup scheduling ignore the state of the corresponding setting.
   * <p/>
   * This doesn't affect other conditions when autopopup is not possible (e.g. power save mode).
   */
  public static final Key<Boolean> ALWAYS_AUTO_POPUP = Key.create("Always Show Completion Auto-Popup");

  /**
   * Setting this user data key to the editor with a completion provider
   * removes from autopopup bottom panel with buttons and hints
   */
  public static final Key<Boolean> SHOW_BOTTOM_PANEL_IN_LOOKUP_UI = Key.create("Show Bottom Panel In Auto-Popup");
  /**
   * Setting this user data key to the editor with a completion provider
   * allows showing auto popup for slashes
   */
  public static final Key<Boolean> ALLOW_AUTO_POPUP_FOR_SLASHES_IN_PATHS = Key.create("Allow Auto-Popup For Slashes In Paths");

  /**
   * If editor has Boolean.TRUE by this key completion popup would be shown without advertising text at the bottom.
   */
  public static final Key<Boolean> NO_ADS = Key.create("Show Completion Auto-Popup without Ads");

  /**
   * If editor has Boolean.TRUE by this key completion popup would be shown every time when editor gets focus.
   * For example this key can be used for TextFieldWithAutoCompletion.
   * (TextFieldWithAutoCompletion looks like standard JTextField and completion shortcut is not obvious to be active)
   */
  public static final Key<Boolean> AUTO_POPUP_ON_FOCUS_GAINED = Key.create("Show Completion Auto-Popup On Focus Gained");


  public static AutoPopupController getInstance(@NotNull Project project) {
    return project.getService(AutoPopupController.class);
  }


  public abstract void autoPopupMemberLookup(@NotNull Editor editor, @Nullable Condition<? super PsiFile> condition);

  public abstract void autoPopupMemberLookup(@NotNull Editor editor, @NotNull CompletionType completionType, @Nullable Condition<? super PsiFile> condition);

  public abstract void scheduleAutoPopup(@NotNull Editor editor,
                                         @NotNull CompletionType completionType,
                                         @Nullable Condition<? super PsiFile> condition);

  public abstract void scheduleAutoPopup(@NotNull Editor editor);

  public abstract void cancelAllRequests();

  public abstract void autoPopupParameterInfo(@NotNull Editor editor, @Nullable PsiElement highlightedMethod);

  @TestOnly
  public abstract void waitForDelayedActions(long timeout, @NotNull TimeUnit unit) throws TimeoutException;
}
