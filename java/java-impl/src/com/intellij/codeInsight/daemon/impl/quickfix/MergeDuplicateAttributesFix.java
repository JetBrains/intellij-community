// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MergeDuplicateAttributesFix extends PsiUpdateModCommandAction<PsiNameValuePair> {
  public MergeDuplicateAttributesFix(PsiNameValuePair element) {
    super(element);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNameValuePair pair, @NotNull ModPsiUpdater updater) {
    PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList)pair.getParent();
    PsiNameValuePair[] attributes = parameterList.getAttributes();
    List<String> strings = new ArrayList<>();
    String name = pair.getName();
    if (name == null) return;
    for (PsiNameValuePair attribute : attributes) {
      String attributeName = attribute.getName();
      if (!name.equals(attributeName) && (attributeName != null || !PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name))) continue;
      PsiAnnotationMemberValue value = attribute.getValue();
      if (value == null) continue;
      CommentTracker ct = new CommentTracker();
      if (value instanceof PsiArrayInitializerMemberValue) {
        if (((PsiArrayInitializerMemberValue)value).getInitializers().length != 0) {
          PsiElement lBrace = value.getFirstChild();
          PsiElement rBrace = value.getLastChild();
          PsiElement maybeTrailingComma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(rBrace);
          // if there is a trailing comma in the array initializer, this allows you to copy comments between the last initializer and
          // the trailing comma to the merged value
          PsiElement to = (PsiUtil.isJavaToken(maybeTrailingComma, JavaTokenType.COMMA) ? maybeTrailingComma : rBrace).getPrevSibling();
          strings.add(ct.rangeText(lBrace.getNextSibling(), to));
        }
      }
      else {
        strings.add(value.getText());
      }
      if (strings.size() != 1) {
        // the check above allows you to get from
        // @SuppressWarnings(value = "foo", value = "bar")
        // to
        // @SuppressWarnings(value = {"foo", "bar"}),
        // but not to
        // @SuppressWarnings({"foo", "bar"})
        ct.deleteAndRestoreComments(value.getParent());
      }
    }
    PsiAnnotation dummyAnnotation =
      JavaPsiFacade.getElementFactory(context.project()).createAnnotationFromText("@A({" + Strings.join(strings, ", ") + "})", null);
    PsiAnnotationMemberValue mergedValue = dummyAnnotation.getParameterList().getAttributes()[0].getValue();
    PsiAnnotation annotation = (PsiAnnotation)parameterList.getParent();
    annotation.setDeclaredAttributeValue(pair.getName(), mergedValue);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("merge.duplicate.attributes.family");
  }
}
