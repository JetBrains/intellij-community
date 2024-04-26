// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.actions;

import com.google.common.base.CharMatcher;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;

final class ShowAsJsonAction extends DumbAwareAction {
  private static final class Holder {
    private static final CharMatcher JSON_START_MATCHER = CharMatcher.is('{');
  }
  private static final Key<Integer> LINE_KEY = Key.create("jsonFileToLogLineNumber");
  private static final Key<WeakReference<Editor>> EDITOR_REF_KEY = Key.create("jsonFileToConsoleEditor");

  private static final class JsonLineExtractor {
    private int start = -1;
    private int end = -1;

    private Document document;

    private int line = -1;
    private int lineStart = -1;

    private JsonLineExtractor(@NotNull Editor editor) {
      doCompute(editor);
    }

    public int getLine() {
      return line;
    }

    private void doCompute(@NotNull Editor editor) {
      SelectionModel model = editor.getSelectionModel();
      document = editor.getDocument();
      if (!model.hasSelection()) {
        int offset = editor.getCaretModel().getOffset();
        if (offset <= document.getTextLength()) {
          line = document.getLineNumber(offset);
          lineStart = document.getLineStartOffset(line);
          getJsonString(document, document.getLineEndOffset(line));
        }
        return;
      }

      lineStart = model.getSelectionStart();
      int end = model.getSelectionEnd();
      line = document.getLineNumber(lineStart);
      if (line == document.getLineNumber(end)) {
        getJsonString(document, end);
      }
    }

    private void getJsonString(Document document, int lineEnd) {
      CharSequence documentChars = document.getCharsSequence();
      int start = Holder.JSON_START_MATCHER.indexIn(documentChars, lineStart);
      if (start < 0) {
        return;
      }

      int end = -1;
      for (int i = lineEnd - 1; i > start; i--) {
        if (documentChars.charAt(i) == '}') {
          end = i;
          break;
        }
      }

      if (end == -1) {
        return;
      }

      this.start = start;
      this.end = end + 1;
    }

    public CharSequence get() {
      return document.getCharsSequence().subSequence(start, end + 1);
    }

    public boolean has() {
      return start != -1;
    }

    public String getPrefix() {
      CharSequence chars = document.getCharsSequence();
      int end = start;
      for (int i = start - 1; i > lineStart; i--) {
        char c = chars.charAt(i);
        if (c == ':' || Character.isWhitespace(c)) {
          end--;
        }
        else {
          break;
        }
      }
      return CharMatcher.whitespace().trimFrom(chars.subSequence(lineStart, end));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    boolean enabled = editor != null && e.getData(LangDataKeys.CONSOLE_VIEW) != null && new JsonLineExtractor(editor).has();
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    final Project project = e.getProject();
    JsonLineExtractor jsonLineExtractor = project == null || editor == null ? null : new JsonLineExtractor(editor);
    if (jsonLineExtractor == null || !jsonLineExtractor.has()) {
      return;
    }

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    if (selectOpened(editor, jsonLineExtractor, fileEditorManager)) {
      return;
    }

    LightVirtualFile virtualFile = new LightVirtualFile(StringUtil.trimMiddle(jsonLineExtractor.getPrefix(), 50), JsonLanguage.INSTANCE, jsonLineExtractor.get());
    virtualFile.putUserData(LINE_KEY, jsonLineExtractor.getLine());
    virtualFile.putUserData(EDITOR_REF_KEY, new WeakReference<>(editor));

    final PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
    if (file == null) {
      return;
    }

    DocumentUtil.writeInRunUndoTransparentAction(() -> CodeStyleManager.getInstance(project).reformat(file, true));

    virtualFile.setWritable(false);
    FileEditorManager.getInstance(project).openFile(virtualFile, true);
  }

  private static boolean selectOpened(Editor editor, JsonLineExtractor jsonLineExtractor, FileEditorManager fileEditorManager) {
    for (FileEditor fileEditor : fileEditorManager.getAllEditors()) {
      if (fileEditor instanceof TextEditor textEditor) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(textEditor.getEditor().getDocument());
        if (file instanceof LightVirtualFile) {
          Integer line = LINE_KEY.get(file);
          if (line != null && line == jsonLineExtractor.getLine()) {
            WeakReference<Editor> editorReference = EDITOR_REF_KEY.get(file);
            if (SoftReference.dereference(editorReference) == editor) {
              fileEditorManager.openFile(file, true, true);
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}