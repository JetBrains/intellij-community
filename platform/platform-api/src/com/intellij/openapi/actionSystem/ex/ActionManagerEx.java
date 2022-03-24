// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public abstract class ActionManagerEx extends ActionManager {
  public static ActionManagerEx getInstanceEx() {
    return (ActionManagerEx)getInstance();
  }

  @NotNull
  public abstract ActionToolbar createActionToolbar(@NotNull String place, @NotNull ActionGroup group, boolean horizontal, boolean decorateButtons);

  /** Do not call directly, prefer {@link ActionUtil} methods. */
  @ApiStatus.Internal
  public abstract void fireBeforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event);

  /** Do not call directly, prefer {@link ActionUtil} methods. */
  @ApiStatus.Internal
  public abstract void fireAfterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result);


  /** @deprecated use {@link #fireBeforeActionPerformed(AnAction, AnActionEvent)} instead */
  @Deprecated(forRemoval = true)
  public final void fireBeforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
    fireBeforeActionPerformed(action, event);
  }

  /** @deprecated use {@link #fireAfterActionPerformed(AnAction, AnActionEvent, AnActionResult)} instead */
  @Deprecated(forRemoval = true)
  public final void fireAfterActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
    fireAfterActionPerformed(action, event, AnActionResult.PERFORMED);
  }

  public abstract void fireBeforeEditorTyping(char c, @NotNull DataContext dataContext);

  public abstract void fireAfterEditorTyping(char c, @NotNull DataContext dataContext);

  /**
   * For logging purposes
   */

  public abstract String getLastPreformedActionId();

  public abstract String getPrevPreformedActionId();

  /**
   * A comparator that compares action ids (String) by the order of action registration.
   *
   * @return a negative integer if the action that corresponds to the first id was registered earlier than the action that corresponds
   *  to the second id; zero if both ids are equal; a positive number otherwise.
   */
  @NotNull
  public abstract Comparator<String> getRegistrationOrderComparator();

  /**
   * Similar to {@link KeyStroke#getKeyStroke(String)} but allows keys in lower case.
   * <p/>
   * I.e. "control x" is accepted and interpreted as "control X".
   *
   * @return null if string cannot be parsed.
   */
  @Nullable
  public static KeyStroke getKeyStroke(@NotNull String s) {
    KeyStroke result = null;
    try {
      result = KeyStroke.getKeyStroke(s);
    }
    catch (Exception ex) {
      //ok
    }
    if (result == null && s.length() >= 2 && s.charAt(s.length() - 2) == ' ') {
      try {
        String s1 = s.substring(0, s.length() - 1) + Character.toUpperCase(s.charAt(s.length() - 1));
        result = KeyStroke.getKeyStroke(s1);
      }
      catch (Exception ignored) {
      }
    }
    return result;
  }


  public abstract String @NotNull [] getPluginActions(@NotNull PluginId pluginId);

  public abstract boolean isActionPopupStackEmpty();

  /**
   * Allows receiving notifications when popup menus created from action groups are shown and hidden.
   */
  public abstract void addActionPopupMenuListener(@NotNull ActionPopupMenuListener listener, @NotNull Disposable parentDisposable);

  @ApiStatus.Internal
  public static void doWithLazyActionManager(@NotNull Consumer<? super ActionManager> whatToDo) {
    ActionManager created = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
    if (created == null) {
      ForkJoinPool.commonPool().execute(() -> {
        ActionManager actionManager = getInstanceEx();
        ApplicationManager.getApplication().invokeLater(() -> whatToDo.accept(actionManager), ModalityState.any());
      });
    } else {
      whatToDo.accept(created);
    }
  }
}

