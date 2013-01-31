/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class JavaReferenceImporter implements ReferenceImporter {
  @Override
  public boolean autoImportReferenceAtCursor(@NotNull final Editor editor, @NotNull final PsiFile file) {
    return autoImportReferenceAtCursor(editor, file, false);
  }

  public static boolean autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file, final boolean allowCaretNearRef) {
    if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) {
      return false;
    }

    int caretOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(caretOffset);
    int startOffset = document.getLineStartOffset(lineNumber);
    int endOffset = document.getLineEndOffset(lineNumber);

    List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
    for (PsiElement element : elements) {
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)element;
        if (ref.multiResolve(true).length == 0) {
          new ImportClassFix(ref).doFix(editor, false, allowCaretNearRef);
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public boolean autoImportReferenceAt(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) {
      return false;
    }

    PsiReference element = file.findReferenceAt(offset);
    if (element instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)element;
      if (ref.multiResolve(true).length == 0) {
        new ImportClassFix(ref).doFix(editor, false, true);
        return true;
      }
    }

    return false;
  }
}
