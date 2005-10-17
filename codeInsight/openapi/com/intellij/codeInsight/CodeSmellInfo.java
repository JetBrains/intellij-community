/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;

public final class CodeSmellInfo {
  @NotNull private final Document myDocument;
  private final String myDescription;
  private final TextRange myTextRange;
  private final HighlightSeverity mySeverity;

  public CodeSmellInfo(@NotNull final Document document, final String description, final TextRange textRange, final HighlightSeverity severity) {
    myDocument = document;
    myDescription = description;
    myTextRange = textRange;
    mySeverity = severity;
  }

  @NotNull public Document getDocument() {
    return myDocument;
  }
  public String getDescription() {
    return myDescription;
  }
  public TextRange getTextRange(){
    return myTextRange;
  }
  public HighlightSeverity getSeverity(){
    return mySeverity;
  }

  public int getStartLine() {
    return getDocument().getLineNumber(getTextRange().getStartOffset());
  }

  public int getStartColumn() {
    return getTextRange().getStartOffset() - getDocument().getLineStartOffset(getStartLine());
  }
}
