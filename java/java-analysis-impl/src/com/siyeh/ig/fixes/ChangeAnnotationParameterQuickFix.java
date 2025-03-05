// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiNameValuePair;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class ChangeAnnotationParameterQuickFix extends PsiUpdateModCommandAction<PsiAnnotation> {
  private final String myName;
  private final String myNewValue;
  private final @IntentionName String myMessage;

  public ChangeAnnotationParameterQuickFix(@NotNull PsiAnnotation annotation, @NotNull String name, @Nullable String newValue,
                                           @IntentionName @NotNull String message) {
    super(annotation);
    myName = name;
    myNewValue = newValue;
    myMessage = message;
  }

  public ChangeAnnotationParameterQuickFix(@NotNull PsiAnnotation annotation, @NotNull String name, @Nullable String newValue) {
    this(annotation, name, newValue,
         newValue == null
         ? InspectionGadgetsBundle.message("remove.annotation.parameter.0.fix.name", name)
         : InspectionGadgetsBundle.message("set.annotation.parameter.0.1.fix.name", name, newValue));
  }

  @Override
  public @NotNull String getFamilyName() {
    return myMessage;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiAnnotation annotation, @NotNull ModPsiUpdater updater) {
    final PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, myName);
    if (myNewValue != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(context.project());
      final PsiAnnotation dummyAnnotation = elementFactory.createAnnotationFromText("@A" + "(" + myName + "=" + myNewValue + ")", null);
      annotation.setDeclaredAttributeValue(myName, dummyAnnotation.getParameterList().getAttributes()[0].getValue());
    }
    else if (attribute != null) {
      new CommentTracker().deleteAndRestoreComments(attribute);
    }
  }
}
