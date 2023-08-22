// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

interface TemplateStateProcessor {
  boolean isUndoOrRedoInProgress(Project project);

  void registerUndoableAction(TemplateState state, Project project, Document document);

  TextRange insertNewLineIndentMarker(PsiFile file, Document document, int offset);

  PsiElement findWhiteSpaceNode(PsiFile file, int offset);

  void logTemplate(Project project, TemplateImpl template, Language language);

  void runLookup(TemplateState state, Project project, Editor editor, LookupElement @NotNull [] items, Expression node);

  boolean isLookupShown();

  boolean skipSettingFinalEditorState(Project project);

  boolean isCaretOutsideCurrentSegment(Editor editor, TemplateSegments segments, int currentSegmentNumber, String commandName);
}
