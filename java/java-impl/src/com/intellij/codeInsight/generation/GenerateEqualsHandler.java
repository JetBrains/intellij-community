// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GenerateEqualsHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance(GenerateEqualsHandler.class);
  private PsiField[] myEqualsFields;
  private PsiField[] myHashCodeFields;
  private PsiField[] myNonNullFields;
  private static final PsiElementClassMember<?>[] DUMMY_RESULT = new PsiElementClassMember[1]; //cannot return empty array, but this result won't be used anyway

  public GenerateEqualsHandler() {
    super("");
  }

  @Override
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = PsiField.EMPTY_ARRAY;

    GlobalSearchScope scope = aClass.getResolveScope();
    DumbService dumbService = DumbService.getInstance(project);
    final PsiMethod equalsMethod = dumbService.computeWithAlternativeResolveEnabled(
      () -> GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getEqualsSignature(project, scope)));
    final PsiMethod hashCodeMethod = dumbService.computeWithAlternativeResolveEnabled(
      () -> GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getHashCodeSignature()));

    boolean needEquals = needToGenerateMethod(equalsMethod);
    boolean needHashCode = needToGenerateMethod(hashCodeMethod);
    if (!needEquals && !needHashCode) {
      String text = aClass instanceof PsiAnonymousClass
                    ? JavaBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
                    : JavaBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.getQualifiedName());

      if (Messages.showYesNoDialog(project, text,
                                   JavaBundle.message("generate.equals.and.hashcode.already.defined.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        if (!WriteAction.compute(() -> {
          try {
            equalsMethod.delete();
            hashCodeMethod.delete();
            return Boolean.TRUE;
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return Boolean.FALSE;
          }
        }).booleanValue()) {
          return null;
        }
        else {
          needEquals = needHashCode = true;
        }
      }
      else {
        return null;
      }
    }
    boolean hasNonStaticFields = hasNonStaticFields(aClass);
    if (!hasNonStaticFields) {
      HintManager.getInstance().showErrorHint(editor, JavaBundle.message("generate.equals.no.fields.for.generation"));
      return null;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myEqualsFields = myHashCodeFields = aClass.getFields();
      myNonNullFields = PsiField.EMPTY_ARRAY;
    } else {
      GenerateEqualsWizard wizard = new GenerateEqualsWizard(project, aClass, needEquals, needHashCode);
      if (!wizard.showAndGet()) {
        return null;
      }
      myEqualsFields = wizard.getEqualsFields();
      myHashCodeFields = wizard.getHashCodeFields();
      myNonNullFields = wizard.getNonNullFields();
    }

    return DUMMY_RESULT;
  }

  static boolean needToGenerateMethod(PsiMethod equalsMethod) {
    return equalsMethod == null || !equalsMethod.isPhysical();
  }

  public static boolean hasNonStaticFields(PsiClass aClass) {
    for (PsiField field : aClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean hasMembers(@NotNull PsiClass aClass) {
    return hasNonStaticFields(aClass);
  }

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] originalMembers) {
    final boolean useInstanceofToCheckParameterType = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;
    final boolean useAccessors = CodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE;

    GenerateEqualsHelper helper = new GenerateEqualsHelper(aClass.getProject(), aClass, myEqualsFields, myHashCodeFields, myNonNullFields,
                                                           useInstanceofToCheckParameterType, useAccessors);
    return OverrideImplementUtil.convert2GenerationInfos(helper.generateMembers());
  }

  @Override
  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    return null;
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) {
    return null;
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = null;
  }
}
