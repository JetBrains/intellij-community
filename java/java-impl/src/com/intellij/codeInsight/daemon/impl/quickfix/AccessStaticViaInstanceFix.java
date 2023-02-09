// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class AccessStaticViaInstanceFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(AccessStaticViaInstanceFix.class);
  private final boolean myOnTheFly;
  private final @IntentionName String myText;

  public AccessStaticViaInstanceFix(@NotNull PsiReferenceExpression expression, @NotNull JavaResolveResult result, boolean onTheFly) {
    super(expression);
    myOnTheFly = onTheFly;
    PsiMember member = (PsiMember)result.getElement();
    myText = calcText(member, result.getSubstitutor());
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  private static @IntentionName String calcText(PsiMember member, PsiSubstitutor substitutor) {
    PsiClass aClass = member.getContainingClass();
    if (aClass == null) return "";
    return QuickFixBundle.message("access.static.via.class.reference.text",
                                  HighlightMessageUtil.getSymbolName(member, substitutor, PsiFormatUtilBase.SHOW_TYPE),
                                  HighlightUtil.formatClass(aClass, false),
                                  HighlightUtil.formatClass(aClass, false));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("access.static.via.class.reference.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiReferenceExpression myExpression = (PsiReferenceExpression)startElement;

    if (!myExpression.isValid()) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(myExpression.getContainingFile())) return;
    PsiElement element = myExpression.resolve();
    if (!(element instanceof PsiMember myMember)) return;
    if (!myMember.isValid()) return;

    PsiClass containingClass = myMember.getContainingClass();
    if (containingClass == null || containingClass instanceof PsiAnonymousClass) return;
    final PsiExpression qualifierExpression = myExpression.getQualifierExpression();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (qualifierExpression != null && !checkSideEffects(project, containingClass, qualifierExpression, factory, myExpression,editor)) return;
    WriteAction.run(() -> {
      try {
        PsiElement newQualifier = factory.createReferenceExpression(containingClass);
        if (qualifierExpression != null) {
          newQualifier = qualifierExpression.replace(newQualifier);
        }
        else {
          myExpression.setQualifierExpression((PsiExpression)newQualifier);
          newQualifier = myExpression.getQualifierExpression();
        }
        PsiElement qualifiedWithClassName = myExpression.copy();
        if (myExpression.getTypeParameters().length == 0 && !(containingClass.isInterface() && !containingClass.equals(PsiTreeUtil.getParentOfType(myExpression, PsiClass.class)))) {
          newQualifier.delete();
          if (myExpression.resolve() != myMember) {
            myExpression.replace(qualifiedWithClassName);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private boolean checkSideEffects(final Project project,
                                   PsiClass containingClass,
                                   final PsiExpression qualifierExpression,
                                   PsiElementFactory factory,
                                   final PsiElement myExpression,
                                   Editor editor) {
    final List<PsiElement> sideEffects = new ArrayList<>();
    boolean hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(qualifierExpression, null, sideEffects);
    if (hasSideEffects && !myOnTheFly) return false;
    if (!hasSideEffects || ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }
    if (editor == null) {
      return false;
    }
    HighlightManager.getInstance(project).addOccurrenceHighlights(editor, PsiUtilCore.toPsiElementArray(sideEffects), 
                                                                  EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
    try {
      hasSideEffects = PsiUtil.isStatement(factory.createStatementFromText(qualifierExpression.getText(), qualifierExpression));
    }
    catch (IncorrectOperationException e) {
      hasSideEffects = false;
    }
    final PsiReferenceExpression qualifiedWithClassName = (PsiReferenceExpression)myExpression.copy();
    qualifiedWithClassName.setQualifierExpression(factory.createReferenceExpression(containingClass));
    final PsiStatement statement = PsiTreeUtil.getParentOfType(myExpression, PsiStatement.class);
    final boolean canCopeWithSideEffects = hasSideEffects && statement != null;
    final SideEffectWarningDialog dialog =
      new SideEffectWarningDialog(project, false, null, sideEffects.get(0).getText(), PsiExpressionTrimRenderer.render(qualifierExpression),
                                  canCopeWithSideEffects){
        @Override
        protected String sideEffectsDescription() {
          if (canCopeWithSideEffects) {
            return MessageFormat.format(getFormatString(),
                                        JavaBundle.message("side.effects.expression.presentation", qualifierExpression.getText()),
                                        myExpression.getText(), //before text
                                        qualifierExpression.getText() + ";" + "<br>" + qualifiedWithClassName.getText());//after text
          }
          return JavaBundle.message("side.effects.non.fixable.message", qualifierExpression.getText());
        }
      };
    dialog.show();
    int res = dialog.getExitCode();
    if (res == RemoveUnusedVariableUtil.RemoveMode.CANCEL.ordinal()) return false;
    if (res == RemoveUnusedVariableUtil.RemoveMode.MAKE_STATEMENT.ordinal()) {
      final PsiStatement statementFromText = factory.createStatementFromText(qualifierExpression.getText() + ";", null);
      LOG.assertTrue(statement != null);
      WriteAction.run(() -> {
        try {
          PsiElement parent = statement.getParent();
          BlockUtils.addBefore(parent instanceof PsiForStatement ? (PsiStatement)parent : statement, statementFromText);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      });
    }
    return true;
  }
}
