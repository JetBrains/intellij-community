// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.contextConfiguration;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public final class SelectedTextFormatter {
  private final Project myProject;
  private final Editor myEditor;
  private final PsiFile myFile;

  private final String myTextBefore;
  private final RangeMarker mySelectionRangeMarker;


  public SelectedTextFormatter(Project project, Editor editor, PsiFile file) {
    myProject = project;
    myEditor = editor;
    myFile = file;

    myTextBefore = myEditor.getSelectionModel().getSelectedText();
    mySelectionRangeMarker = myEditor.getDocument().createRangeMarker(myEditor.getSelectionModel().getSelectionStart(),
                                                                      myEditor.getSelectionModel().getSelectionEnd());
  }

  public void restoreSelectedText() {
    final Document document = myEditor.getDocument();
    if (!mySelectionRangeMarker.isValid()) return;
    final int start = mySelectionRangeMarker.getStartOffset();
    final int end = mySelectionRangeMarker.getEndOffset();

    WriteCommandAction.writeCommandAction(myProject)
                      .withName(LangBundle.message("command.name.configure.code.style.on.selected.fragment.restore.text.before"))
                      .run(() -> document.replaceString(start, end, myTextBefore));

    myEditor.getSelectionModel().setSelection(start, start + myTextBefore.length());
  }

  void reformatSelectedText(@NotNull CodeStyleSettings reformatSettings) {
    final SelectionModel model = myEditor.getSelectionModel();
    if (model.hasSelection()) {
      CodeStyle.runWithLocalSettings(myProject, reformatSettings, () -> reformatRange(myFile, getSelectedRange()));
    }
  }

  void reformatWholeFile() {
    reformatRange(myFile, myFile.getTextRange());
  }

  private static void reformatRange(final @NotNull PsiFile file, final @NotNull TextRange range) {
    final Project project = file.getProject();
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> CodeStyleManager.getInstance(project).reformatText(file, range.getStartOffset(), range.getEndOffset())),
                                                  CodeStyleBundle.message("command.name.reformat"), null);
  }

  @NotNull
  TextRange getSelectedRange() {
    SelectionModel model = myEditor.getSelectionModel();
    int start = model.getSelectionStart();
    int end = model.getSelectionEnd();
    return TextRange.create(start, end);
  }
}