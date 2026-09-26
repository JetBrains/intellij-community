// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NonInteractiveTemplateStateProcessor implements TemplateStateProcessor {
  @Override
  public boolean isUndoOrRedoInProgress(Project project) {
    return false;
  }

  @Override
  public void registerUndoableAction(TemplateState state, Project project, Document document) {
  }

  @Override
  public TextRange insertNewLineIndentMarker(PsiFile file, Document document, int offset) {
    return null;
  }

  @Override
  public PsiElement findWhiteSpaceNode(PsiFile file, int offset) {
    return null;
  }

  @Override
  public void logTemplate(Project project, TemplateImpl template, Language language) {
  }

  @Override
  public void runLookup(TemplateState state, Project project, Editor editor, LookupElement @NotNull [] items, Expression node) {
  }

  @Override
  public boolean isLookupShown() {
    return false;
  }

  @Override
  public boolean skipSettingFinalEditorState(Project project) {
    return true;
  }

  @Override
  public boolean isCaretOutsideCurrentSegment(Editor editor, TemplateSegments segments, int currentSegmentNumber, String commandName) {
    return false;
  }
}
