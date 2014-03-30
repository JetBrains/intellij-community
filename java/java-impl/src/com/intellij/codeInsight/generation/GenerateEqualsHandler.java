/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author dsl
 */
public class GenerateEqualsHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateEqualsHandler");
  private PsiField[] myEqualsFields = null;
  private PsiField[] myHashCodeFields = null;
  private PsiField[] myNonNullFields = null;
  private static final PsiElementClassMember[] DUMMY_RESULT = new PsiElementClassMember[1]; //cannot return empty array, but this result won't be used anyway

  public GenerateEqualsHandler() {
    super("");
  }

  @Override
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = PsiField.EMPTY_ARRAY;


    GlobalSearchScope scope = aClass.getResolveScope();
    final PsiMethod equalsMethod = GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getEqualsSignature(project, scope));
    final PsiMethod hashCodeMethod = GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getHashCodeSignature());

    boolean needEquals = equalsMethod == null;
    boolean needHashCode = hashCodeMethod == null;
    if (!needEquals && !needHashCode) {
      String text = aClass instanceof PsiAnonymousClass
                    ? CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
                    : CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.getQualifiedName());

      if (Messages.showYesNoDialog(project, text,
                                   CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        if (!ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
            @Override
            public Boolean compute() {
              try {
                equalsMethod.delete();
                hashCodeMethod.delete();
                return Boolean.TRUE;
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
                return Boolean.FALSE;
              }
            }
          }).booleanValue()) {
          return null;
        } else {
          needEquals = needHashCode = true;
        }
      } else {
        return null;
      }
    }
    boolean hasNonStaticFields = false;
    for (PsiField field : aClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)){
        hasNonStaticFields = true;
        break;
      }
    }
    if (!hasNonStaticFields) {
      HintManager.getInstance().showErrorHint(editor, "No fields to include in equals/hashCode have been found");
      return null;
    }

    GenerateEqualsWizard wizard = new GenerateEqualsWizard(project, aClass, needEquals, needHashCode);
    wizard.show();
    if (!wizard.isOK()) return null;
    myEqualsFields = wizard.getEqualsFields();
    myHashCodeFields = wizard.getHashCodeFields();
    myNonNullFields = wizard.getNonNullFields();
    return DUMMY_RESULT;
  }

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] originalMembers) throws IncorrectOperationException {
    Project project = aClass.getProject();
    final boolean useInstanceofToCheckParameterType = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;

    GenerateEqualsHelper helper = new GenerateEqualsHelper(project, aClass, myEqualsFields, myHashCodeFields, myNonNullFields,
                                                           useInstanceofToCheckParameterType);
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
