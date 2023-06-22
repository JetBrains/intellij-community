// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.StripTrailingSpacesFilter;
import com.intellij.openapi.editor.StripTrailingSpacesFilterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public abstract class PsiBasedStripTrailingSpacesFilter implements StripTrailingSpacesFilter {
  private final @NotNull BitSet myDisabledLinesBitSet;
  private final @NotNull Document myDocument;

  private static final Logger LOG = Logger.getInstance(PsiBasedStripTrailingSpacesFilter.class);
  
  public abstract static class Factory extends StripTrailingSpacesFilterFactory {
    @Override
    public final @NotNull StripTrailingSpacesFilter createFilter(@Nullable Project project, @NotNull Document document) {
      Language language = getDocumentLanguage(document);
      if (language != null && isApplicableTo(language)) {
        PsiFile psiFile = getPsiFile(project, document);
        if (psiFile != null) {
          PsiBasedStripTrailingSpacesFilter filter = createFilter(document);
          filter.process(psiFile);
          return filter;
        }
        return POSTPONED;
      }
      return ALL_LINES;
    }

    protected abstract @NotNull PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document);
    
    protected abstract boolean isApplicableTo(@NotNull Language language);
  }

  protected PsiBasedStripTrailingSpacesFilter(@NotNull Document document) {
    myDocument = document;
    myDisabledLinesBitSet = new BitSet(document.getLineCount());
  }

  @Override
  public boolean isStripSpacesAllowedForLine(int line) {
    return !myDisabledLinesBitSet.get(line);
  }
  
  
  protected abstract void process(@NotNull PsiFile psiFile);

  public static @Nullable Language getDocumentLanguage(@NotNull Document document) {
    FileDocumentManager manager = FileDocumentManager.getInstance();
    VirtualFile file = manager.getFile(document);
    if (file != null && file.isValid()) {
      return LanguageUtil.getFileLanguage(file);
    }
    return null;
  }
  
  private static @Nullable PsiFile getPsiFile(@Nullable Project project, @NotNull Document document) {
    if (project != null) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      if (documentManager.isCommitted(document)) {
        return documentManager.getCachedPsiFile(document);
      }
    }
    else {
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      LOG.warn(
        "No current project is given, trailing spaces will be stripped later (postponed). File: " +
        (virtualFile != null ? virtualFile.getCanonicalPath() : "undefined"));
    }
    return null;
  }

  protected final void disableRange(@NotNull TextRange range, boolean includeEndLine) {
    int startLine = myDocument.getLineNumber(range.getStartOffset());
    int endLine = myDocument.getLineNumber(range.getEndOffset());
    if (includeEndLine) {
      endLine++;
    }
    myDisabledLinesBitSet.set(startLine, endLine);
  }
}
