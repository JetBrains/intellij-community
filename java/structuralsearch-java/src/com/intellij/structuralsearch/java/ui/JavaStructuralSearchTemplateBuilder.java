// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.java.ui;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchTemplateBuilder;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaStructuralSearchTemplateBuilder extends StructuralSearchTemplateBuilder {
  @Override
  public TemplateBuilder buildTemplate(@NotNull PsiFile psiFile) {
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(psiFile);
    PlaceholderCount classCount = new PlaceholderCount("Class");
    PlaceholderCount varCount = new PlaceholderCount("Var");
    PlaceholderCount funCount = new PlaceholderCount("Fun");

    IntRef shift = new IntRef();
    JavaRecursiveElementVisitor visitor = new JavaRecursiveElementVisitor() {

      @Override
      public void visitIdentifier(@NotNull PsiIdentifier identifier) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiClass) {
          replaceElement(identifier, classCount, true, builder, shift.get());
        }
        else if (parent instanceof PsiReferenceExpression) {
          if (parent.getParent() instanceof PsiMethodCallExpression)
            replaceElement(identifier, funCount, true, builder, shift.get());
          else
            replaceElement(identifier, varCount, false, builder, shift.get());
        }
        else if (parent instanceof PsiJavaCodeReferenceElement) {
          replaceElement(identifier, classCount, false, builder, shift.get());
        }
      }

      @Override
      public void visitReferenceList(@NotNull PsiReferenceList list) {
        PsiJavaCodeReferenceElement[] elements = list.getReferenceElements();
        for (PsiJavaCodeReferenceElement element : elements) {
          replaceElement(element.getReferenceNameElement(), classCount, false, builder, shift.get());
        }
      }
    };

    String text = psiFile.getText();
    int textOffset = 0;
    while (textOffset < text.length() && StringUtil.isWhiteSpace(text.charAt(textOffset))) {
      textOffset++;
    }
    shift.set(shift.get() - textOffset);
    PsiElement[] elements =
      MatcherImplUtil.createTreeFromText(text, PatternTreeContext.Block, (LanguageFileType)psiFile.getFileType(), psiFile.getProject());
    if (elements.length > 0) {
      PsiElement element = elements[0];
      shift.set(shift.get() + element.getTextRange().getStartOffset());
      element.accept(visitor);
    }
    return builder;
  }

  void replaceElement(@Nullable PsiElement element, PlaceholderCount count, boolean preferOriginal, TemplateBuilder builder, int shift) {
    if (element == null) {
      return;
    }
    String placeholder = count.getPlaceholder();
    String originalText = element.getText();
    LookupElement[] elements = {LookupElementBuilder.create(placeholder), LookupElementBuilder.create(originalText)};
    builder.replaceRange(element.getTextRange().shiftLeft(shift),
                         new ConstantNode(preferOriginal ? originalText : placeholder)
                           .withLookupItems(preferOriginal ? ArrayUtil.reverseArray(elements) : elements));
  }

  private static final class PlaceholderCount {
    private final String myName;
    private int myCount;

    private PlaceholderCount(String name) {
      myName = name;
    }

    public String getPlaceholder() {
      return "$" + myName + ++myCount + "$";
    }
  }
}
