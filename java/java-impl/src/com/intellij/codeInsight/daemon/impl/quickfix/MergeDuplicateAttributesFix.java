// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.StringJoiner;

public class MergeDuplicateAttributesFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public MergeDuplicateAttributesFix(PsiElement element) {
    super(element);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiNameValuePair pair = (PsiNameValuePair)startElement;
    final PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList)pair.getParent();
    final PsiNameValuePair[] attributes = parameterList.getAttributes();
    final StringJoiner joiner = new StringJoiner(", ");
    final String name = pair.getName();
    final boolean[] isFirstValue = {true};
    StreamEx.of(attributes).filterBy(PsiNameValuePair::getName, name).map(attribute -> attribute.getValue()).filter(Objects::nonNull)
      .forEach(value -> {
        CommentTracker ct = new CommentTracker();
        if (value instanceof PsiArrayInitializerMemberValue) {
          if (((PsiArrayInitializerMemberValue)value).getInitializers().length == 0 && !isFirstValue[0]) {
            new CommentTracker().deleteAndRestoreComments(value.getParent());
            return;
          }
          else {
            final PsiElement lBrace = value.getFirstChild();
            final PsiElement lastChild = value.getLastChild();
            PsiElement maybeTrailingComma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(lastChild);
            if (PsiUtil.isJavaToken(maybeTrailingComma, JavaTokenType.COMMA)) {
              maybeTrailingComma = maybeTrailingComma.getPrevSibling();
            }
            if (maybeTrailingComma == null) return;
            joiner.add(ct.rangeText(lBrace.getNextSibling(), maybeTrailingComma));
          }
        }
        else {
          joiner.add(value.getText());
        }
        if (!isFirstValue[0]) {
          ct.deleteAndRestoreComments(value.getParent());
        }
        isFirstValue[0] = false;
      });
    final PsiAnnotation dummyAnnotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@A({" + joiner + "})", null);
    final PsiAnnotationMemberValue mergedValue = dummyAnnotation.getParameterList().getAttributes()[0].getValue();
    final PsiAnnotation annotation = (PsiAnnotation)parameterList.getParent();
    annotation.setDeclaredAttributeValue(pair.getName(), mergedValue);
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("merge.duplicate.attributes.family");
  }
}
