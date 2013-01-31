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
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CreateMethodQuickFix extends IntentionAndQuickFixAction {
  protected final PsiClass myTargetClass;
  protected final String mySignature;
  protected final String myBody;

  private CreateMethodQuickFix(final PsiClass targetClass, @NonNls final String signature, @NonNls final String body) {
    myTargetClass = targetClass;
    mySignature = signature;
    myBody = body;
  }

  @Override
  @NotNull
  public String getName() {

    String signature = PsiFormatUtil.formatMethod(createMethod(myTargetClass.getProject()), PsiSubstitutor.EMPTY,
                                                  PsiFormatUtilBase.SHOW_NAME |
                                                  PsiFormatUtilBase.SHOW_TYPE |
                                                  PsiFormatUtilBase.SHOW_PARAMETERS |
                                                  PsiFormatUtilBase.SHOW_RAW_TYPE,
                                                  PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_RAW_TYPE, 2);
    return QuickFixBundle.message("create.method.from.usage.text", signature);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Override
  public void applyFix(Project project, PsiFile file, @Nullable Editor editor) {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(myTargetClass.getContainingFile())) return;

    PsiMethod method = createMethod(project);
    List<Pair<PsiExpression, PsiType>> arguments =
      ContainerUtil.map2List(method.getParameterList().getParameters(), new Function<PsiParameter, Pair<PsiExpression, PsiType>>() {
        @Override
        public Pair<PsiExpression, PsiType> fun(PsiParameter psiParameter) {
          return Pair.create(null, psiParameter.getType());
        }
      });

    method = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(myTargetClass.add(method));
    CreateMethodFromUsageFix.doCreate(myTargetClass, method, arguments, PsiSubstitutor.EMPTY, ExpectedTypeInfo.EMPTY_ARRAY, method);
  }

  private PsiMethod createMethod(Project project) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    String methodText = mySignature + (myTargetClass.isInterface() ? ";" : "{" + myBody + "}");
    return elementFactory.createMethodFromText(methodText, null);
  }

  @Nullable
  public static CreateMethodQuickFix createFix(@NotNull PsiClass targetClass, @NonNls final String signature, @NonNls final String body) {
    CreateMethodQuickFix fix = new CreateMethodQuickFix(targetClass, signature, body);
    try {
      fix.createMethod(targetClass.getProject());
      return fix;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }
}
