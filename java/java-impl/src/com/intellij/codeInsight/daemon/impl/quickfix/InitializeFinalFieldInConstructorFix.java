// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.PsiElementResult;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.options.OptMultiSelector.OptElement;
import com.intellij.modcommand.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class InitializeFinalFieldInConstructorFix extends PsiBasedModCommandAction<PsiField> {
  private static final Logger LOG = Logger.getInstance(InitializeFinalFieldInConstructorFix.class);

  public InitializeFinalFieldInConstructorFix(@NotNull PsiField field) {
    super(field);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("initialize.final.field.in.constructor.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasInitializer()) return null;

    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null || containingClass.getName() == null) return null;

    return Presentation.of(getFamilyName());
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiField field) {
    final PsiClass myClass = field.getContainingClass();
    if (myClass == null) return ModCommand.nop();
    if (myClass.getConstructors().length == 0) {
      return ModCommand.psiUpdate(context, updater -> {
        PsiClass writableClass = updater.getWritable(myClass);
        PsiField writableField = updater.getWritable(field);
        PsiMethod ctor = AddDefaultConstructorFix.addDefaultConstructor(writableClass);
        addFieldInitialization(List.of(ctor), writableField, updater);
      });
    }

    List<PsiMethod> ctors =
      CreateConstructorParameterFromFieldFix.filterConstructorsIfFieldAlreadyAssigned(myClass.getConstructors(), field);
    for (Iterator<PsiMethod> iterator = ctors.iterator(); iterator.hasNext(); ) {
      PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(iterator.next());
      if (JavaPsiConstructorUtil.isChainedConstructorCall(constructorCall)) {
        // otherwise final field can be initialized multiple times, which does not compile
        iterator.remove();
      }
    }

    List<PsiMethodMember> allMembers = ContainerUtil.map(ctors, PsiMethodMember::new);
    if (ctors.size() == 1) {
      return getFinalCommand(field, allMembers);
    }

    return ModCommand.chooseMultipleMembers(QuickFixBundle.message("initialize.final.field.in.constructor.choose.dialog.title"),
                                            allMembers, chosenMembers -> getFinalCommand(field, chosenMembers));
  }

  private static @NotNull ModCommand getFinalCommand(@NotNull PsiField field, List<? extends @NotNull OptElement> chosenMembers) {
    return ModCommand.psiUpdate(field, (writableField, updater) -> {
      List<PsiMethod> writableConstructors =
        ContainerUtil.map(chosenMembers, member -> updater.getWritable(((PsiMethodMember)member).getElement()));
      addFieldInitialization(writableConstructors, writableField, updater);
    });
  }

  private static void addFieldInitialization(@NotNull List<? extends PsiMethod> constructors,
                                             @NotNull PsiField field,
                                             @NotNull ModPsiUpdater updater) {
    Project project = field.getProject();
    if (constructors.isEmpty()) return;

    final LookupElement[] suggestedInitializers = AddVariableInitializerFix.suggestInitializer(field);

    final List<SmartPsiElementPointer<PsiExpression>> rExprPointers = new ArrayList<>(constructors.size());
    for (PsiMethod constructor : constructors) {
      PsiExpression initializer = addFieldInitialization(constructor, suggestedInitializers, field, project);
      rExprPointers.add(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(initializer));
    }
    Document doc = Objects.requireNonNull(field.getContainingFile().getViewProvider().getDocument());
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc);
    List<PsiExpression> rExpressions = ContainerUtil.mapNotNull(rExprPointers, SmartPsiElementPointer::getElement);
    runAssignmentTemplate(rExpressions, suggestedInitializers, updater);
  }

  private static void runAssignmentTemplate(final @NotNull List<? extends PsiExpression> initializers,
                                            final LookupElement @NotNull [] suggestedInitializers,
                                            @NotNull ModPsiUpdater updater) {
    LOG.assertTrue(!initializers.isEmpty());
    final PsiExpression initializer = Objects.requireNonNull(ContainerUtil.getFirstItem(initializers));
    PsiElement context = initializers.size() == 1 ? initializer : PsiTreeUtil.findCommonParent(initializers);
    if (context == null) return;
    ModTemplateBuilder builder = updater.templateBuilder();
    for (PsiExpression e : initializers) {
      builder.field(e, new ConstantNode(new PsiElementResult(suggestedInitializers[0].getPsiElement())).withLookupItems(
        suggestedInitializers));
    }
  }

  private static @NotNull PsiExpression addFieldInitialization(@NotNull PsiMethod constructor,
                                                               LookupElement @NotNull [] suggestedInitializers,
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
    String stmtText = fieldName + " = " + Objects.requireNonNull(suggestedInitializers[0].getPsiElement()).getText() + ";";
    if (methodContainsParameterWithName(constructor, fieldName)) {
      stmtText = "this." + stmtText;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiManager.getProject());
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final PsiExpressionStatement addedStatement = (PsiExpressionStatement)methodBody.add(codeStyleManager
      .reformat(factory.createStatementFromText(stmtText, methodBody)));
    return Objects.requireNonNull(((PsiAssignmentExpression)addedStatement.getExpression()).getRExpression());
  }

  private static boolean methodContainsParameterWithName(@NotNull PsiMethod constructor, @NotNull String name) {
    return ContainerUtil.exists(constructor.getParameterList().getParameters(), parameter -> name.equals(parameter.getName()));
  }
}
