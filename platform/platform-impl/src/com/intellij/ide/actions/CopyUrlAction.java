// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.util.regex.Matcher;

public final class CopyUrlAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String url = findUrlAtCaret(e);
    assert url != null;
    CopyPasteManager.getInstance().setContents(new StringSelection(url));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(findUrlAtCaret(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static @Nullable String findUrlAtCaret(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;

    int offset = editor.getCaretModel().getOffset();
    String url = findUrl(editor, offset);
    if (url != null) return url;

    int expectedCaretOffset = ((EditorEx)editor).getExpectedCaretOffset();
    return expectedCaretOffset == offset ? null : findUrl(editor, expectedCaretOffset);
  }

  private static String findUrl(Editor editor, int offset) {
    CharSequence chars = editor.getDocument().getCharsSequence();
    while (offset > 0 && seemsUrlPart(chars.charAt(offset - 1))) offset--;

    Matcher matcher = URLUtil.URL_PATTERN.matcher(chars.subSequence(offset, chars.length()));
    if (matcher.lookingAt()) {
      return matcher.group();
    }
    return null;
  }

  private static boolean seemsUrlPart(char c) {
    return !Character.isWhitespace(c) && c != '(' && c != '[' && c != '<' && c != '{' && c != ',';
  }
}
