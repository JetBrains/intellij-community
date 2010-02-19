/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AccessStaticViaInstanceFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix");
  private final PsiReferenceExpression myExpression;
  private final boolean myOnTheFly;
  private final PsiMember myMember;
  private final JavaResolveResult myResult;

  public AccessStaticViaInstanceFix(PsiReferenceExpression expression, JavaResolveResult result, boolean onTheFly) {
    myExpression = expression;
    myOnTheFly = onTheFly;
    myMember = (PsiMember)result.getElement();
    myResult = result;
  }

  @NotNull
  public String getName() {
    PsiClass aClass = myMember.getContainingClass();
    if (aClass == null) return "";
    return QuickFixBundle.message("access.static.via.class.reference.text",
                                  HighlightMessageUtil.getSymbolName(myMember, myResult.getSubstitutor()),
                                  HighlightUtil.formatClass(aClass),
                                  HighlightUtil.formatClass(aClass,false));
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("access.static.via.class.reference.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!myExpression.isValid() || !myMember.isValid()) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(myExpression.getContainingFile())) return;
    PsiClass containingClass = myMember.getContainingClass();
    if (containingClass == null) return;
    try {
      final PsiExpression qualifierExpression = myExpression.getQualifierExpression();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      if (qualifierExpression != null) {
        if (!checkSideEffects(project, containingClass, qualifierExpression, factory)) return;
        PsiElement newQualifier = qualifierExpression.replace(factory.createReferenceExpression(containingClass));
        PsiElement qualifiedWithClassName = myExpression.copy();
        newQualifier.delete();
        if (myExpression.resolve() != myMember) {
          myExpression.replace(qualifiedWithClassName);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private boolean checkSideEffects(final Project project, PsiClass containingClass, final PsiExpression qualifierExpression,
                                   PsiElementFactory factory) {
    final List<PsiElement> sideEffects = new ArrayList<PsiElement>();
    boolean hasSideEffects = RemoveUnusedVariableFix.checkSideEffects(qualifierExpression, null, sideEffects);
    if (hasSideEffects && !myOnTheFly) return false;
    if (hasSideEffects && !ApplicationManager.getApplication().isUnitTestMode()) {
      final TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      final Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, sideEffects.toArray(new PsiElement[sideEffects.size()]), attributes, true, null);
      try {
        hasSideEffects = PsiUtil.isStatement(factory.createStatementFromText(qualifierExpression.getText(), qualifierExpression));
      }
      catch (IncorrectOperationException e) {
        hasSideEffects = false;
      }
      final PsiReferenceExpression qualifiedWithClassName = (PsiReferenceExpression)myExpression.copy();
      qualifiedWithClassName.setQualifierExpression(factory.createReferenceExpression(containingClass));
      final boolean canCopeWithSideEffects = hasSideEffects;
      final SideEffectWarningDialog dialog =
        new SideEffectWarningDialog(project, false, null, sideEffects.get(0).getText(), qualifierExpression.getText(),
                                    canCopeWithSideEffects){
          @Override
          protected String sideEffectsDescription() {
            if (canCopeWithSideEffects) {
              return "<html><body>" +
                     "  There are possible side effects found in expression '" +
                     qualifierExpression.getText() +
                     "'<br>" +
                     "  You can:<ul><li><b>Remove</b> class reference along with whole expressions involved, or</li>" +
                     "  <li><b>Transform</b> qualified expression into the statement on its own.<br>" +
                     "  That is,<br>" +
                     "  <table border=1><tr><td><code>" +
                     myExpression.getText() +
                     "</code></td></tr></table><br> becomes: <br>" +
                     "  <table border=1><tr><td><code>" +
                     qualifierExpression.getText() +
                     ";<br>" +
                     qualifiedWithClassName.getText() +
                     "       </code></td></tr></table></li>" +
                     "  </body></html>";
            } else {
              return "<html><body>  There are possible side effects found in expression '" + qualifierExpression.getText() + "'<br>" +
                     "You can:<ul><li><b>Remove</b> class reference along with whole expressions involved, or</li></body></html>";
            }
          }
        };
      dialog.show();
      int res = dialog.getExitCode();
      if (res == SideEffectWarningDialog.CANCEL) return false;
      try {
        if (res == SideEffectWarningDialog.MAKE_STATEMENT) {
          final PsiStatement statementFromText = factory.createStatementFromText(qualifierExpression.getText() + ";", null);
          final PsiStatement statement = PsiTreeUtil.getParentOfType(myExpression, PsiStatement.class);
          statement.getParent().addBefore(statementFromText, statement);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return true;
  }
}
