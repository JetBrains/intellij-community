package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class CompletionContext {
  public static final Key<CompletionContext> COMPLETION_CONTEXT_KEY = Key.create("CompletionContext");

  public final Project project;
  public final Editor editor;
  public final PsiFile file;
  private OffsetMap myOffsetMap;

  private String myPrefix = "";

  public CompletionContext(Project project, Editor editor, PsiFile file, final OffsetMap offsetMap){
    this.project = project;
    this.editor = editor;
    this.file = file;
    myOffsetMap = offsetMap;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public void setPrefix(PsiElement insertedElement, int offset, @Nullable CompletionData completionData) {
    setPrefix(completionData == null ? CompletionData.findPrefixStatic(insertedElement, offset) : completionData.findPrefix(insertedElement, offset));
  }

  public void setPrefix(final String prefix) {
    myPrefix = prefix;
  }

  public int getStartOffset() {
    return getOffsetMap().getOffset(CompletionInitializationContext.START_OFFSET);
  }

  public void setStartOffset(final int newStartOffset) {
    getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, newStartOffset, false);
  }

  public int getSelectionEndOffset() {
    return getOffsetMap().getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  public void setSelectionEndOffset(final int selectionEndOffset) {
    getOffsetMap().addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, selectionEndOffset, true);
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }
}

