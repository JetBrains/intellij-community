// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class TerminalActionUtil {

  private TerminalActionUtil() {}

  public static @Nullable TerminalAction createTerminalAction(@NotNull JBTerminalWidget widget,
                                                              @NonNls @NotNull String actionId,
                                                              boolean hiddenAction) {
    List<KeyStroke> keyStrokes = JBTerminalSystemSettingsProviderBase.getKeyStrokesByActionId(actionId);
    if (keyStrokes.isEmpty() && hiddenAction) return null;
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) {
      throw new AssertionError("Cannot find action " + actionId);
    }
    String name = action.getTemplateText();
    if (name != null && !hiddenAction) {
      throw new AssertionError("Action has unknown name: " + actionId);
    }
    TerminalActionPresentation presentation = new TerminalActionPresentation(StringUtil.notNullize(name, "unknown"), keyStrokes);
    return new TerminalAction(presentation, (keyEvent) -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(widget.getTerminalPanel());
      ActionUtil.performAction(action, AnActionEvent.createFromInputEvent(keyEvent, "Terminal", null, dataContext));
      return true;
    }).withHidden(hiddenAction);
  }

  public static @NotNull TerminalAction createTerminalAction(@NotNull JBTerminalWidget widget,
                                                             @NonNls @NotNull AnAction action) {
    Collection<KeyStroke> strokes = KeymapUtil.getKeyStrokes(action.getShortcutSet());
    String name = StringUtil.notNullize(action.getTemplateText(), "unknown");
    return new TerminalAction(new TerminalActionPresentation(name, List.copyOf(strokes)), (keyEvent) -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(widget.getTerminalPanel());
      ActionUtil.performAction(action, AnActionEvent.createFromInputEvent(keyEvent, "Terminal", null, dataContext));
      return true;
    });
  }

  public static TerminalAction createTerminalAction(@NotNull JBTerminalWidget widget,
                                                    @NotNull TerminalActionPresentation actionPresentation,
                                                    @NotNull Predicate<? super JBTerminalWidgetListener> action) {
    return new TerminalAction(actionPresentation, input -> {
      JBTerminalWidgetListener listener = widget.getListener();
      if (listener != null) {
        return action.test(listener);
      }
      return false;
    }).withEnabledSupplier(() -> widget.getListener() != null);
  }
}
