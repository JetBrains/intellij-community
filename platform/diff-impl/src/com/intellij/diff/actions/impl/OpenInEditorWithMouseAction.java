// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public abstract class OpenInEditorWithMouseAction extends AnAction implements DumbAware {
  private @NotNull List<? extends Editor> myEditors = Collections.emptyList();

  public OpenInEditorWithMouseAction() {
    AnAction navigateAction = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_DECLARATION); // null in MPS
    setShortcutSet(navigateAction != null ?
                   navigateAction.getShortcutSet() :
                   new CustomShortcutSet(new MouseShortcut(MouseEvent.BUTTON1, InputEvent.CTRL_DOWN_MASK, 1)));
  }

  public void install(@NotNull List<? extends Editor> editors) {
    myEditors = editors;
    for (Editor editor : editors) {
      registerCustomShortcutSet(getShortcutSet(), (EditorGutterComponentEx)editor.getGutter());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    InputEvent inputEvent = e.getInputEvent();
    if (!(inputEvent instanceof MouseEvent)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (e.getProject() == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (e.getData(DiffDataKeys.DIFF_CONTEXT) == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Component component = inputEvent.getComponent();
    if (component == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Point point = ((MouseEvent)inputEvent).getPoint();
    Component componentAt = SwingUtilities.getDeepestComponentAt(component, point.x, point.y);
    if (!(componentAt instanceof EditorGutterComponentEx)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Editor editor = getEditor(componentAt);
    if (editor == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    MouseEvent convertedEvent = SwingUtilities.convertMouseEvent(inputEvent.getComponent(), (MouseEvent)inputEvent, componentAt);
    EditorMouseEventArea area = editor.getMouseEventArea(convertedEvent);
    if (area != EditorMouseEventArea.LINE_NUMBERS_AREA) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (!(e.getInputEvent() instanceof MouseEvent inputEvent) ||
        inputEvent.getComponent() == null) return;
    Runnable callback = e.getData(OpenInEditorAction.AFTER_NAVIGATE_CALLBACK);

    Component component = inputEvent.getComponent();
    Point point = inputEvent.getPoint();
    Component componentAt = SwingUtilities.getDeepestComponentAt(component, point.x, point.y);
    MouseEvent convertedEvent = SwingUtilities.convertMouseEvent(inputEvent.getComponent(), inputEvent, componentAt);

    Editor editor = getEditor(componentAt);
    assert editor != null;

    int line = editor.xyToLogicalPosition(convertedEvent.getPoint()).line;

    Navigatable navigatable = getNavigatable(editor, line);
    if (navigatable == null) return;

    OpenInEditorAction.openEditor(project, navigatable, callback);
  }

  private @Nullable Editor getEditor(@NotNull Component component) {
    for (Editor editor : myEditors) {
      if (editor != null && editor.getGutter() == component) {
        return editor;
      }
    }
    return null;
  }

  protected abstract @Nullable Navigatable getNavigatable(@NotNull Editor editor, int line);
}
