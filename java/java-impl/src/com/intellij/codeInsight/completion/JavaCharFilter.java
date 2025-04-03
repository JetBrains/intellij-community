// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

public final class JavaCharFilter extends CharFilter {

  private static boolean isWithinLiteral(final Lookup lookup) {
    PsiElement psiElement = lookup.getPsiElement();
    return psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression;
  }

  @Override
  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    PsiFile file = lookup.getPsiFile();
    if (file == null) return null;
    boolean isJava = file.getLanguage().isKindOf(JavaLanguage.INSTANCE);
    boolean isJsp = file.getFileType() == StdFileTypes.JSP;
    if (!isJava && !isJsp) {
      return null;
    }

    LookupElement item = lookup.getCurrentItem();
    if (item == null || !item.isValid()) return null;

    final Object o = item.getObject();
    if (c == '!' || c == '?') {
      JavaPsiClassReferenceElement typeItem = item.as(JavaPsiClassReferenceElement.class);
      if (typeItem != null && typeItem.getInsertHandler() instanceof JavaClassNameInsertHandler) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
    }
    if (c == '{' || c == '@') {
      PsiElement element = lookup.getPsiElement();
      if (element instanceof PsiDocComment || element instanceof PsiDocToken ||
          element instanceof PsiWhiteSpace && element.getParent() instanceof PsiDocComment) {
        return Result.ADD_TO_PREFIX;
      }
    }
    if (c == '!') {
      VariableLookupItem varItem = item.as(VariableLookupItem.class);
      if (varItem != null && varItem.isNegatable()) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;

      JavaMethodCallElement methodItem = item.as(JavaMethodCallElement.class);
      if (methodItem != null && methodItem.isNegatable()) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;

      if (o instanceof PsiKeyword && ((PsiKeyword)o).textMatches(JavaKeywords.INSTANCEOF)) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }

      return null;
    }
    if (c == '.' && isWithinLiteral(lookup)) return Result.ADD_TO_PREFIX;

    if (c == ':') {
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(lookup.getEditor().getDocument());
      PsiElement leaf = file.findElementAt(lookup.getEditor().getCaretModel().getOffset() - 1);
      if (PsiUtil.isAvailable(JavaFeature.METHOD_REFERENCES, file)) {
        PsiStatement statement = PsiTreeUtil.getParentOfType(leaf, PsiStatement.class);
        if (statement == null ||
            statement.getTextRange().getStartOffset() != leaf.getTextRange().getStartOffset()) { // not typing a statement label
          return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
        }
      }
      if (PsiTreeUtil.getParentOfType(leaf, PsiSwitchLabelStatement.class) != null ||
          PsiTreeUtil.getParentOfType(leaf, PsiConditionalExpression.class) != null) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
      return Result.HIDE_LOOKUP;
    }


    if (c == '[' || c == ']' || c == ')' || c == '>') return CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    if (c == '<' && o instanceof PsiClass) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    if (c == '(') {
      if (o instanceof PsiClass) {
        if (PsiJavaPatterns.psiElement().afterLeaf(JavaKeywords.NEW).accepts(lookup.getPsiElement())) {
          return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
        }
        return Result.HIDE_LOOKUP;
      }
      if (o instanceof PsiType) {
        return Result.HIDE_LOOKUP;
      }
    }
    if ((c == ',' || c == '=') && o instanceof PsiVariable) {
      int lookupStart = lookup.getLookupStart();
      String name = ((PsiVariable)o).getName();
      if (lookupStart >= 0 && name != null && name.equals(lookup.itemPattern(item))) {
        return Result.HIDE_LOOKUP;
      }
    }

    if (c == '#' && PsiTreeUtil.getParentOfType(lookup.getPsiElement(), PsiDocComment.class) != null) {
      if (o instanceof PsiClass) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
    }
    if (c == '(' && JavaKeywords.RETURN.equals(item.getLookupString())) {
      return Result.HIDE_LOOKUP;
    }
    return null;
  }

}
