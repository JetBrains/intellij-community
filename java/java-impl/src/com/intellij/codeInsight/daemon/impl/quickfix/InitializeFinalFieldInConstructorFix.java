// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InitializeFinalFieldInConstructorFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(InitializeFinalFieldInConstructorFix.class);
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

    ApplicationManager.getApplication().runWriteAction(() -> addFieldInitialization(constructors, myField, project, editor));
  }

  private static void addFieldInitialization(@NotNull List<PsiMethod> constructors,
                                             @NotNull PsiField field,
                                             @NotNull Project project,
                                             @Nullable Editor editor) {
    if (constructors.isEmpty()) return;

    final LookupElement[] suggestedInitializers = AddVariableInitializerFix.suggestInitializer(field);

    final List<PsiExpression> rExpressions = new ArrayList<>(constructors.size());
    for (PsiMethod constructor : constructors) {
      rExpressions.add(addFieldInitialization(constructor, suggestedInitializers, field, project));
    }
    Document doc = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(field.getContainingFile()));
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc);
    AddVariableInitializerFix.runAssignmentTemplate(rExpressions, suggestedInitializers, editor);
  }

  @NotNull
  private static PsiExpression addFieldInitialization(@NotNull PsiMethod constructor,
                                                      @NotNull LookupElement[] suggestedInitializers,
                                                      @NotNull PsiField field,
                                                      @NotNull Project project) {
    PsiCodeBlock methodBody = constructor.getBody();
    if (methodBody == null) {
      //incomplete code
      CreateFromUsageUtils.setupMethodBody(constructor);
      methodBody = constructor.getBody();
      LOG.assertTrue(methodBody != null);
    }

    final String fieldName = field.getName();
    String stmtText = fieldName + " = " + suggestedInitializers[0].getPsiElement().getText() + ";";
    if (methodContainsParameterWithName(constructor, fieldName)) {
      stmtText = "this." + stmtText;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final PsiExpressionStatement addedStatement = (PsiExpressionStatement)methodBody.add(codeStyleManager
      .reformat(factory.createStatementFromText(stmtText, methodBody)));
    return ObjectUtils.notNull(((PsiAssignmentExpression)addedStatement.getExpression()).getRExpression());
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
      final MemberChooser<PsiMethodMember> chooser = new MemberChooser<>(toPsiMethodMemberArray(ctors), false, true, project);
      chooser.setTitle(QuickFixBundle.message("initialize.final.field.in.constructor.choose.dialog.title"));
      chooser.show();

      final List<PsiMethodMember> chosenMembers = chooser.getSelectedElements();
      if (chosenMembers != null) {
        return Arrays.asList(toPsiMethodArray(chosenMembers));
      }
    }

    return Collections.emptyList();
  }

  @NotNull
  private static PsiMethodMember[] toPsiMethodMemberArray(@NotNull PsiMethod[] methods) {
    final PsiMethodMember[] result = new PsiMethodMember[methods.length];
    for (int i = 0; i < methods.length; i++) {
      result[i] = new PsiMethodMember(methods[i]);
    }
    return result;
  }

  @NotNull
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
    ApplicationManager.getApplication().runWriteAction(() -> defaultConstructorFix.invoke(project, editor, file));
  }

  @NotNull
  private static PsiMethod[] filterIfFieldAlreadyAssigned(@NotNull PsiField field, @NotNull PsiMethod[] ctors) {
    final List<PsiMethod> result = new ArrayList<>(Arrays.asList(ctors));
    for (PsiReference reference : ReferencesSearch.search(field, new LocalSearchScope(ctors))) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)element)) {
        result.remove(PsiTreeUtil.getParentOfType(element, PsiMethod.class));
      }
    }
    return result.toArray(new PsiMethod[result.size()]);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
