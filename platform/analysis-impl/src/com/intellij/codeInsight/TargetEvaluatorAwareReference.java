// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.model.Symbol;
import com.intellij.model.SymbolResolveResult;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.codeInsight.TargetElementUtilBase.ELEMENT_NAME_ACCEPTED;
import static com.intellij.codeInsight.TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED;

@Internal
public final class TargetEvaluatorAwareReference implements PsiSymbolReference {

  private static final int ourDefaultFlags = ELEMENT_NAME_ACCEPTED | REFERENCED_ELEMENT_ACCEPTED;

  private final PsiReference myReference;

  public TargetEvaluatorAwareReference(@NotNull PsiReference reference) {
    myReference = reference;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myReference.getElement();
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return myReference.getRangeInElement();
  }

  @Override
  public @NotNull TextRange getAbsoluteRange() {
    return myReference.getAbsoluteRange();
  }

  @Override
  public boolean resolvesTo(@NotNull Symbol target) {
    return myReference.resolvesTo(target);
  }

  @Override
  public @NotNull Collection<? extends SymbolResolveResult> resolveReference() {
    final PsiElement targetElement = getTargetElement();
    if (targetElement != null) {
      final Symbol symbol = PsiSymbolService.getInstance().asSymbol(targetElement);
      return Collections.singletonList(SymbolResolveResult.fromSymbol(symbol));
    }
    else {
      return myReference.resolveReference(); // handles poly-variant case as well
    }
  }

  /**
   * This method partially reproduces what happens within {@link TargetElementUtilBase#getReferencedElement(PsiFile, int, int, Editor, PsiElement)}.
   */
  private @Nullable PsiElement getTargetElement() {
    final PsiElement referencedElement = TargetElementUtilBase.getReferencedElement(myReference, ourDefaultFlags);
    final PsiFile file = getElement().getContainingFile();
    final Project project = file.getProject();

    final int offsetInFile = getAbsoluteRange().getStartOffset();
    final PsiElement leafElement = file.findElementAt(offsetInFile);

    final PsiElement adjustedElement;
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) {
      adjustedElement = referencedElement;
    }
    else {
      final Language language = leafElement == null ? file.getLanguage() : leafElement.getLanguage();
      final TargetElementEvaluatorEx2 evaluatorEx2 = TargetElementUtilBase.getElementEvaluatorsEx2(language);
      if (evaluatorEx2 == null) {
        adjustedElement = referencedElement;
      }
      else {
        Editor editor = mockEditor(project, document);
        adjustedElement = evaluatorEx2.adjustReferenceOrReferencedElement(file, editor, offsetInFile, ourDefaultFlags, referencedElement);
      }
    }
    return TargetElementUtilBase.isAcceptableReferencedElement(leafElement, adjustedElement) ? adjustedElement : null;
  }

  @NotNull
  private static Editor mockEditor(@NotNull Project project, @NotNull Document document) {
    return new ImaginaryEditor(document) {

      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public String toString() {
        return "API compatibility editor";
      }
    };
  }
}
