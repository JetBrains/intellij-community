/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

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

/**
 * @author peter
 */
public class CopyUrlAction extends DumbAwareAction {
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

  @Nullable
  private static String findUrlAtCaret(@NotNull AnActionEvent e) {
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
