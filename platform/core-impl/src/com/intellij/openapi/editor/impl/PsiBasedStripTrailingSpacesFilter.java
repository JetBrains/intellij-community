/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.StripTrailingSpacesFilter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public abstract class PsiBasedStripTrailingSpacesFilter extends StripTrailingSpacesFilter {
  private @Nullable BitSet myDisabledLinesBitSet;
  private final @NotNull Document myDocument;

  protected PsiBasedStripTrailingSpacesFilter(@Nullable Project project, @NotNull Document document) {
    myDocument = document;
    setDocumentLevelPermission(process(project, document));
  }

  @Override
  public boolean isStripSpacesAllowedForLine(int line) {
    return myDisabledLinesBitSet != null && myDisabledLinesBitSet.get(line);
  }

  public final DocumentLevelPermission process(@Nullable Project project, @NotNull Document document) {
    Language language = getDocumentLanguage(document);
    if (language != null && isApplicableTo(language)) {
      myDisabledLinesBitSet = new BitSet(document.getLineCount());
      PsiFile psiFile = getPsiFile(project, document);
      if (psiFile != null) {
        process(document, psiFile);
        return DocumentLevelPermission.FILTER_BY_LINE;
      }
      return DocumentLevelPermission.POSTPONED;
    }
    return DocumentLevelPermission.ALL_LINES;
  }
  
  protected abstract boolean isApplicableTo(@NotNull Language language);
  
  protected abstract void process(@NotNull Document document, @NotNull PsiFile psiFile);

  @Nullable
  private static Language getDocumentLanguage(@NotNull Document document) {
    FileDocumentManager manager = FileDocumentManager.getInstance();
    VirtualFile file = manager.getFile(document);
    if (file != null && file.isValid()) {
      return LanguageUtil.getFileLanguage(file);
    }
    return null;
  }
  
  @Nullable
  private static PsiFile getPsiFile(@Nullable Project project, @NotNull Document document) {
    if (project != null) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      if (documentManager.isCommitted(document)) {
        return documentManager.getCachedPsiFile(document);
      }
    }
    return null;
  }

  protected final void disableRange(@NotNull TextRange range) {
    if (myDisabledLinesBitSet != null) {
      int startLine = myDocument.getLineNumber(range.getStartOffset());
      int endLine = myDocument.getLineNumber(range.getEndOffset());
      myDisabledLinesBitSet.set(startLine, endLine);
    }
  }
}
