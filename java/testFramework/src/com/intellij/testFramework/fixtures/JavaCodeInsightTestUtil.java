// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.refactoring.inline.InlineConstantFieldProcessor;
import com.intellij.refactoring.inline.InlineLocalHandler;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.InlineParameterHandler;
import com.intellij.refactoring.util.InlineUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaCodeInsightTestUtil {
  private static final int TARGET_FOR_INLINE_FLAGS =
    TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;

  private JavaCodeInsightTestUtil() { }

  public static void doInlineLocalTest(@NotNull final CodeInsightTestFixture fixture,
                                       @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    final Editor editor = fixture.getEditor();
    final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
    assert element instanceof PsiLocalVariable : element;
    InlineLocalHandler.inlineVariable(fixture.getProject(), editor, (PsiLocalVariable)element, null);
    fixture.checkResultByFile(after, false);
  }

  public static void doInlineParameterTest(@NotNull final CodeInsightTestFixture fixture,
                                           @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    final Editor editor = fixture.getEditor();
    final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
    assert element instanceof PsiParameter : element;
    new InlineParameterHandler().inlineElement(fixture.getProject(), editor, element);
    fixture.checkResultByFile(after, false);
  }

  public static void doInlineMethodTest(@NotNull final CodeInsightTestFixture fixture,
                                        @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    final Editor editor = fixture.getEditor();
    final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
    assert element instanceof PsiMethod : element;

    final PsiReference ref = fixture.getFile().findReferenceAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;

    final PsiMethod method = (PsiMethod)element;
    assert !(InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method)) : "Bad returns found";
    new InlineMethodProcessor(fixture.getProject(), method, refExpr, editor, false).run();
    fixture.checkResultByFile(after, false);
  }

  public static void doInlineConstantTest(@NotNull final CodeInsightTestFixture fixture,
                                          @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    final Editor editor = fixture.getEditor();
    final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
    assert element instanceof PsiField : element;

    final PsiReference ref = fixture.getFile().findReferenceAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;

    new InlineConstantFieldProcessor((PsiField)element, fixture.getProject(), refExpr, false).run();
    fixture.checkResultByFile(after, false);
  }
}