/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InitializeFinalFieldInConstructorFix implements IntentionAction {
  private final PsiField myField;

  public InitializeFinalFieldInConstructorFix(@NotNull PsiField field) {
    myField = field;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("initialize.final.field.in.constructor.name");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myField.isValid() || myField.hasModifierProperty(PsiModifier.STATIC) || myField.hasInitializer()) {
      return false;
    }

    final PsiClass containingClass = myField.getContainingClass();
    if (containingClass == null || containingClass.getName() == null){
      return false;
    }

    final PsiManager manager = myField.getManager();
    return manager != null && manager.isInProject(myField);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    final PsiClass myClass = myField.getContainingClass();
    if (myClass == null) {
      return;
    }
    if (myClass.getConstructors().length == 0) {
      createDefaultConstructor(myClass, project, editor, file);
    }

    final List<PsiMethod> constructors = choose(filterIfFieldAlreadyAssigned(myField, myClass.getConstructors()), project);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final List<PsiExpressionStatement> statements = addFieldInitialization(constructors, myField, project);
        final PsiExpressionStatement highestStatement = getHighestElement(statements);
        if (highestStatement == null) return;

        final PsiAssignmentExpression expression = (PsiAssignmentExpression)highestStatement.getExpression();
        final PsiElement rightExpression = expression.getRExpression();

        final TextRange expressionRange = rightExpression.getTextRange();
        editor.getCaretModel().moveToOffset(expressionRange.getStartOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(expressionRange.getStartOffset(), expressionRange.getEndOffset());
      }
    });
  }

  @Nullable
  private static <T extends PsiElement> T getHighestElement(@NotNull List<T> elements) {
    T highest = null;
    int highestTextOffset = Integer.MAX_VALUE;
    for (T element : elements) {
      final T forcedElem = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
      final int startOffset = forcedElem.getTextOffset();
      if (startOffset < highestTextOffset) {
        highest = forcedElem;
        highestTextOffset = startOffset;
      }
    }
    return highest;
  }

  @NotNull
  private static List<PsiExpressionStatement> addFieldInitialization(@NotNull List<PsiMethod> constructors,
                                                                     @NotNull PsiField field,
                                                                     @NotNull Project project) {
    final List<PsiExpressionStatement> statements = new ArrayList<PsiExpressionStatement>();
    for (PsiMethod constructor : constructors) {
      final PsiExpressionStatement statement = addFieldInitialization(constructor, field, project);
      if (statement != null) {
        statements.add(statement);
      }
    }
    return statements;
  }

  @Nullable
  private static PsiExpressionStatement addFieldInitialization(@NotNull PsiMethod constructor,
                                                               @NotNull PsiField field,
                                                               @NotNull Project project) {
    PsiCodeBlock methodBody = constructor.getBody();
    if (methodBody == null) return null;

    final String fieldName = field.getName();
    String stmtText = fieldName + " = " + suggestInitValue(field) + ";";
    if (methodContainsParameterWithName(constructor, fieldName)) {
      stmtText = "this." + stmtText;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    return (PsiExpressionStatement)methodBody.add(codeStyleManager.reformat(factory.createStatementFromText(stmtText, methodBody)));
  }

  private static boolean methodContainsParameterWithName(@NotNull PsiMethod constructor, @NotNull String name) {
    for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
      if (name.equals(parameter.getName())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static List<PsiMethod> choose(@NotNull PsiMethod[] ctors, @NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return Arrays.asList(ctors);
    }

    if (ctors.length == 1) {
      return Arrays.asList(ctors[0]);
    }

    if (ctors.length > 1) {
      final MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(toPsiMethodMemberArray(ctors), false, true, project);
      chooser.setTitle(QuickFixBundle.message("initialize.final.field.in.constructor.choose.dialog.title"));
      chooser.show();

      final List<PsiMethodMember> chosenMembers = chooser.getSelectedElements();
      if (chosenMembers != null) {
        return Arrays.asList(toPsiMethodArray(chosenMembers));
      }
    }

    return Collections.emptyList();
  }

  private static PsiMethodMember[] toPsiMethodMemberArray(@NotNull PsiMethod[] methods) {
    final PsiMethodMember[] result = new PsiMethodMember[methods.length];
    for (int i = 0; i < methods.length; i++) {
      result[i] = new PsiMethodMember(methods[i]);
    }
    return result;
  }

  private static PsiMethod[] toPsiMethodArray(@NotNull List<PsiMethodMember> methodMembers) {
    final PsiMethod[] result = new PsiMethod[methodMembers.size()];
    int i = 0;
    for (PsiMethodMember methodMember : methodMembers) {
      result[i++] = methodMember.getElement();
    }
    return result;
  }

  private static void createDefaultConstructor(PsiClass psiClass, @NotNull final Project project, final Editor editor, final PsiFile file) {
    final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(psiClass);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        defaultConstructorFix.invoke(project, editor, file);
      }
    });
  }

  private static PsiMethod[] filterIfFieldAlreadyAssigned(@NotNull PsiField field, @NotNull PsiMethod[] ctors) {
    final List<PsiMethod> result = new ArrayList<PsiMethod>(Arrays.asList(ctors));
    for (PsiReference reference : ReferencesSearch.search(field, new LocalSearchScope(ctors))) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)element)) {
        result.remove(PsiTreeUtil.getParentOfType(element, PsiMethod.class));
      }
    }
    return result.toArray(new PsiMethod[result.size()]);
  }

  private static String suggestInitValue(@NotNull PsiField field) {
    PsiType type = field.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
