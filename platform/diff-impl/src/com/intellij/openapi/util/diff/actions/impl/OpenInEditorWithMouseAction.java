package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

public abstract class OpenInEditorWithMouseAction extends AnAction implements DumbAware {
  @NotNull private List<? extends Editor> myEditors = Collections.emptyList();

  public OpenInEditorWithMouseAction() {
    final AnAction emptyAction = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_DECLARATION); // TODO: configurable
    setShortcutSet(emptyAction.getShortcutSet());
  }

  public void register(@NotNull List<? extends Editor> editors) {
    myEditors = editors;
    for (Editor editor : editors) {
      registerCustomShortcutSet(getShortcutSet(), (EditorGutterComponentEx)editor.getGutter());
    }
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

    if (e.getData(OpenInEditorAction.KEY) == null) {
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
    MouseEvent inputEvent = (MouseEvent)e.getInputEvent();
    OpenInEditorAction openInEditorAction = e.getRequiredData(OpenInEditorAction.KEY);
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    Component component = inputEvent.getComponent();
    Point point = inputEvent.getPoint();
    Component componentAt = SwingUtilities.getDeepestComponentAt(component, point.x, point.y);
    MouseEvent convertedEvent = SwingUtilities.convertMouseEvent(inputEvent.getComponent(), inputEvent, componentAt);

    Editor editor = getEditor(componentAt);
    assert editor != null;

    int line = editor.xyToLogicalPosition(convertedEvent.getPoint()).line;

    OpenFileDescriptor descriptor = getDescriptor(editor, line);
    if (descriptor == null) return;

    openInEditorAction.openEditor(project, descriptor);
  }

  @Nullable
  private Editor getEditor(@NotNull Component component) {
    for (Editor editor : myEditors) {
      if (editor.getGutter() == component) {
        return editor;
      }
    }
    return null;
  }

  @Nullable
  protected abstract OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line);
}
