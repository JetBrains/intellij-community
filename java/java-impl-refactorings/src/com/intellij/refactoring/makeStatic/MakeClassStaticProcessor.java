// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.makeStatic;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MakeClassStaticProcessor extends MakeMethodOrClassStaticProcessor<PsiClass> {
  private static final Logger LOG = Logger.getInstance(MakeClassStaticProcessor.class);
  private final List<PsiField> myFieldsToSplit = new ArrayList<>();

  public MakeClassStaticProcessor(final Project project, final PsiClass aClass, final Settings settings) {
    super(project, aClass, settings);
  }

  @Override
  protected void changeSelf(final PsiElementFactory factory, final UsageInfo[] usages) throws IncorrectOperationException {
    PsiClass containingClass = myMember.getContainingClass();

    //Add fields
    if (mySettings.isMakeClassParameter()) {
      PsiType type = factory.createType(containingClass, PsiSubstitutor.EMPTY);
      final String classParameterName = mySettings.getClassParameterName();
      final String fieldName = convertToFieldName(classParameterName);
      PsiField field = factory.createField(fieldName, type);
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      myMember.add(field);
    }

    if (mySettings.isMakeFieldParameters()) {
      List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

      for (Settings.FieldParameter fieldParameter : parameters) {
        final PsiType type = fieldParameter.type;
        final PsiField field = factory.createField(convertToFieldName(fieldParameter.name), type);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
        myMember.add(field);
      }
    }


    PsiMethod[] constructors = myMember.getConstructors();

    if (constructors.length == 0) {
      final PsiMethod defConstructor = (PsiMethod)myMember.add(factory.createConstructor());
      constructors = new PsiMethod[]{defConstructor};
    }

    boolean generateFinalParams = JavaCodeStyleSettings.getInstance(myMember.getContainingFile()).GENERATE_FINAL_PARAMETERS;
    for (PsiMethod constructor : constructors) {
      final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(constructor);
      PsiParameterList paramList = constructor.getParameterList();
      PsiElement addParameterAfter = null;
      PsiDocTag anchor = null;

      if (mySettings.isMakeClassParameter()) {
        // Add parameter for object
        PsiType parameterType = factory.createType(containingClass, PsiSubstitutor.EMPTY);
        final String classParameterName = mySettings.getClassParameterName();
        PsiParameter parameter = factory.createParameter(classParameterName, parameterType);
        PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, makeClassParameterFinal(usages) || generateFinalParams);
        addParameterAfter = paramList.addAfter(parameter, null);
        anchor = javaDocHelper.addParameterAfter(classParameterName, null);

        addAssignmentToField(classParameterName, constructor);

      }

      if (mySettings.isMakeFieldParameters()) {
        List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

        for (Settings.FieldParameter fieldParameter : parameters) {
          final PsiType fieldParameterType = fieldParameter.field.getType();
          final PsiParameter parameter = factory.createParameter(fieldParameter.name, fieldParameterType);
          PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL,
                                      makeFieldParameterFinal(fieldParameter.field, usages) || generateFinalParams);
          addParameterAfter = paramList.addAfter(parameter, addParameterAfter);
          anchor = javaDocHelper.addParameterAfter(fieldParameter.name, anchor);
          addAssignmentToField(fieldParameter.name, constructor);
        }
        for (UsageInfo usage : usages) {
          if (usage instanceof InternalUsageInfo) {
            final PsiElement element = usage.getElement();
            final PsiElement referencedElement = ((InternalUsageInfo)usage).getReferencedElement();
            if (referencedElement instanceof PsiField && mySettings.getNameForField((PsiField)referencedElement) != null) {
              final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
              if (field != null) {
                MoveInstanceMembersUtil.moveInitializerToConstructor(factory, constructor, field);
              }
            }
          }
        }
      }
      for (PsiField field : myFieldsToSplit) {
        MoveInstanceMembersUtil.moveInitializerToConstructor(factory, constructor, field);
      }
    }


    setupTypeParameterList(myMember);

    // Add static modifier
    final PsiModifierList modifierList = myMember.getModifierList();
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
    modifierList.setModifierProperty(PsiModifier.FINAL, false);
  }

  private void addAssignmentToField(final String parameterName, final PsiMethod constructor) {
    @NonNls String fieldName = convertToFieldName(parameterName);
    final PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    final PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      try {
        final PsiReferenceExpression refExpr = (PsiReferenceExpression)factory.createExpressionFromText(fieldName, body);
        if (refExpr.resolve() != null) fieldName = "this." + fieldName;
        PsiStatement statement = factory.createStatementFromText(fieldName + "=" + parameterName + ";", null);
        statement = (PsiStatement)CodeStyleManager.getInstance(manager.getProject()).reformat(statement);
        body.add(statement);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private @NotNull String convertToFieldName(final String parameterName) {
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(myProject);
    final String propertyName = manager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);
    final String fieldName = manager.propertyNameToVariableName(propertyName, VariableKind.FIELD);
    return fieldName;
  }

  @Override
  protected void changeSelfUsage(final SelfUsageInfo usageInfo) throws IncorrectOperationException {
    PsiElement parent = usageInfo.getElement().getParent();
    LOG.assertTrue(parent instanceof PsiCallExpression); //either this() or new()
    PsiCallExpression call = (PsiCallExpression) parent;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());
    PsiExpressionList args = call.getArgumentList();
    PsiElement addParameterAfter = null;

    if(mySettings.isMakeClassParameter()) {
      PsiElement arg = factory.createExpressionFromText(convertToFieldName(mySettings.getClassParameterName()), null);
      addParameterAfter = args.addAfter(arg, null);
    }

    if(mySettings.isMakeFieldParameters()) {
      List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();
      for (Settings.FieldParameter fieldParameter : parameters) {
        PsiElement arg = factory.createExpressionFromText(convertToFieldName(fieldParameter.name), null);
        if (addParameterAfter == null) {
          addParameterAfter = args.addAfter(arg, null);
        }
        else {
          addParameterAfter = args.addAfter(arg, addParameterAfter);
        }
      }
    }
  }

  @Override
  protected void changeInternalUsage(final InternalUsageInfo usage, final PsiElementFactory factory) throws IncorrectOperationException {
    if (!mySettings.isChangeSignature()) return;

    PsiElement element = usage.getElement();

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression newRef = null;

      if (mySettings.isMakeFieldParameters()) {
        PsiElement resolved = ((PsiReferenceExpression) element).resolve();
        if (resolved instanceof PsiField) {
          String name = mySettings.getNameForField((PsiField)resolved);
          if (name != null) {
            name = convertToFieldName(name);
            newRef = (PsiReferenceExpression) factory.createExpressionFromText(name, null);
          }
        }
      }

      if (newRef == null && mySettings.isMakeClassParameter()) {
        newRef =
        (PsiReferenceExpression) factory.createExpressionFromText(
                convertToFieldName(mySettings.getClassParameterName()) + "." + element.getText(), null);
      }

      if (newRef != null) {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
        newRef = (PsiReferenceExpression) codeStyleManager.reformat(newRef);
        element.replace(newRef);
      }
    }
    else if (mySettings.isMakeClassParameter() && (element instanceof PsiQualifiedExpression)) {
      final PsiElement replace =
        element.replace(factory.createExpressionFromText(convertToFieldName(mySettings.getClassParameterName()), null));
      final PsiField field = PsiTreeUtil.getParentOfType(replace, PsiField.class);
      if (field != null) {
        myFieldsToSplit.add(field);
      }
    }
    else if (element instanceof PsiNewExpression newExpression && mySettings.isMakeClassParameter()) {
      LOG.assertTrue(newExpression.getQualifier() == null);
      final String newText = convertToFieldName(mySettings.getClassParameterName()) + "." + newExpression.getText();
      final PsiExpression expr = factory.createExpressionFromText(newText, null);
      element.replace(expr);
    }
  }

  @Override
  protected void changeExternalUsage(final UsageInfo usage, final PsiElementFactory factory) throws IncorrectOperationException {
    final PsiElement element = usage.getElement();
    if (!(element instanceof PsiJavaCodeReferenceElement methodRef)) return;

    PsiElement parent = methodRef.getParent();
    if (parent instanceof PsiAnonymousClass) {
      parent = parent.getParent();
    }
    LOG.assertTrue(parent instanceof PsiCallExpression, "call expression expected, found " + parent);

    PsiCallExpression call = (PsiCallExpression)parent;

    PsiExpression instanceRef;

    instanceRef = call instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)call).getMethodExpression().getQualifierExpression() :
                  ((PsiNewExpression)call).getQualifier();
    PsiElement newQualifier;

    if (instanceRef == null || instanceRef instanceof PsiSuperExpression) {
      final PsiClass thisClass = RefactoringChangeUtil.getThisClass(element);
      @NonNls String thisText;
      if (thisClass.getManager().areElementsEquivalent(thisClass, myMember.getContainingClass())) {
        thisText = "this";
      }
      else {
        thisText = myMember.getContainingClass().getName() + ".this";
      }
      instanceRef = factory.createExpressionFromText(thisText, null);
      newQualifier = null;
    }
    else {
      newQualifier = factory.createReferenceExpression(myMember.getContainingClass());
    }

    if (mySettings.getNewParametersNumber() > 1) {
      int copyingSafetyLevel = RefactoringUtil.verifySafeCopyExpression(instanceRef);
      if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
        String tempVar = RefactoringUtil.createTempVar(instanceRef, call, true);
        instanceRef = factory.createExpressionFromText(tempVar, null);
      }
    }


    PsiElement anchor = null;
    PsiExpressionList argList = call.getArgumentList();
    PsiExpression[] exprs = argList.getExpressions();
    if (mySettings.isMakeClassParameter()) {
      if (exprs.length > 0) {
        anchor = argList.addBefore(instanceRef, exprs[0]);
      }
      else {
        anchor = argList.add(instanceRef);
      }
    }


    if (mySettings.isMakeFieldParameters()) {
      List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

      for (Settings.FieldParameter fieldParameter : parameters) {
        PsiReferenceExpression fieldRef;
        final String fieldName = fieldParameter.field.getName();
        if (newQualifier != null) {
          fieldRef = (PsiReferenceExpression)factory.createExpressionFromText(
            "a." + fieldName, null);
          fieldRef.getQualifierExpression().replace(instanceRef);
        }
        else {
          fieldRef = (PsiReferenceExpression)factory.createExpressionFromText(fieldName, null);
        }

        if (anchor != null) {
          anchor = argList.addAfter(fieldRef, anchor);
        }
        else {
          if (exprs.length > 0) {
            anchor = argList.addBefore(fieldRef, exprs[0]);
          }
          else {
            anchor = argList.add(fieldRef);
          }
        }
      }
    }

    if (newQualifier != null) {
      if (call instanceof PsiMethodCallExpression) {
        instanceRef.replace(newQualifier);
      } else {
        final PsiAnonymousClass anonymousClass = ((PsiNewExpression)call).getAnonymousClass();
        if (anonymousClass != null) {
          ((PsiNewExpression)call).getQualifier().delete();
          final PsiJavaCodeReferenceElement baseClassReference = anonymousClass.getBaseClassReference();
          baseClassReference.replace(((PsiNewExpression)factory.createExpressionFromText("new " + newQualifier.getText() + "." + baseClassReference.getText() + "()", baseClassReference)).getClassReference());
        } else {
          PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)call).getClassReference();
          LOG.assertTrue(classReference != null);
          final PsiNewExpression newExpr =
            (PsiNewExpression)factory.createExpressionFromText("new " + newQualifier.getText() + "." + classReference.getText() + "()", classReference);
          final PsiExpressionList callArgs = call.getArgumentList();
          if (callArgs != null) {
            final PsiExpressionList argumentList = newExpr.getArgumentList();
            LOG.assertTrue(argumentList != null);
            argumentList.replace(callArgs);
          }
          call.replace(newExpr);
        }
      }
    }
  }

  @Override
  protected MultiMap<PsiElement,String> getConflictDescriptions(final UsageInfo[] usages) {
    final MultiMap<PsiElement, @Nls String> conflicts = super.getConflictDescriptions(usages);

    if (mySettings.isMakeClassParameter()) {
      //Check fields already exist
      final @NlsSafe String fieldName = convertToFieldName(mySettings.getClassParameterName());
      final PsiField existing = myMember.findFieldByName(fieldName, false);
      if (existing != null) {
        String message = JavaRefactoringBundle.message("there.is.already.a.0.in.1", RefactoringUIUtil.getDescription(existing, false),
                                              RefactoringUIUtil.getDescription(myMember, false));
              conflicts.putValue(existing, message);
      }

      for (UsageInfo usage : usages) {
        if (usage instanceof InternalUsageInfo) {
          PsiElement element = usage.getElement();
          PsiClassInitializer classInitializer = PsiTreeUtil.getParentOfType(element, PsiClassInitializer.class);
          if (classInitializer != null && classInitializer.getContainingClass() == myMember) {
            conflicts.putValue(element, JavaRefactoringBundle.message("field.0.won.t.be.initialized.already.in.class.initializer", fieldName));
            break;
          }
        }
      }
    }

    if (mySettings.isMakeFieldParameters()) {
      final List<Settings.FieldParameter> parameterOrderList = mySettings.getParameterOrderList();
      for (Settings.FieldParameter parameter : parameterOrderList) {
        final String fieldName = convertToFieldName(parameter.name);
        final PsiField existing = myMember.findFieldByName(fieldName, false);

        if (existing != null) {
          String message = JavaRefactoringBundle.message("there.is.already.a.0.in.1", RefactoringUIUtil.getDescription(existing, false),
                                                RefactoringUIUtil.getDescription(myMember, false));
          conflicts.putValue(existing, message);
        }
      }
    }

    return conflicts;
  }

  @Override
  protected void findExternalUsages(final ArrayList<UsageInfo> result) {
    PsiMethod[] constructors = myMember.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod constructor : constructors) {
        findExternalReferences(constructor, result);
      }
    } else {
      findDefaultConstructorReferences(result);
    }
  }

  private void findDefaultConstructorReferences(final ArrayList<UsageInfo> result) {
    for (PsiReference ref : ReferencesSearch.search(myMember)) {
      PsiElement element = ref.getElement();
      if (element.getParent() instanceof PsiNewExpression newExpression) {
        PsiElement qualifier = newExpression.getQualifier();
        if (qualifier instanceof PsiThisExpression) qualifier = null;
        if (!PsiTreeUtil.isAncestor(myMember, element, true) || qualifier != null) {
          result.add(new UsageInfo(element));
        } else {
          result.add(new InternalUsageInfo(element, myMember));
        }
      }
    }
  }
}
