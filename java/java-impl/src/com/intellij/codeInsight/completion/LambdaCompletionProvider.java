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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class LambdaCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile())) return;
    final ExpectedTypeInfo[] expectedTypes = JavaSmartCompletionContributor.getExpectedTypes(parameters);
    for (ExpectedTypeInfo expectedType : expectedTypes) {
      final PsiType defaultType = expectedType.getDefaultType();
      if (LambdaUtil.isFunctionalType(defaultType)) {
        final PsiType functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
        final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        if (method != null) {
          PsiParameter[] params = method.getParameterList().getParameters();
          final Project project = method.getProject();
          final PsiElement originalPosition = parameters.getOriginalPosition();
          final JVMElementFactory jvmElementFactory = originalPosition != null ? JVMElementFactories.getFactory(originalPosition.getLanguage(), project) : null;
          final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
          if (jvmElementFactory != null) {
            final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(method, PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
            params = GenerateMembersUtil.overriddenParameters(params, jvmElementFactory, javaCodeStyleManager, substitutor, originalPosition);
          }

          String paramsString =
            params.length == 1 ? getParamName(params[0], javaCodeStyleManager, originalPosition) : "(" + StringUtil.join(params, new Function<PsiParameter, String>() {
            @Override
            public String fun(PsiParameter parameter) {
              return getParamName(parameter, javaCodeStyleManager, originalPosition);
            }
            }, ",") + ")";

          final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
          PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)JavaPsiFacade.getElementFactory(project)
            .createExpressionFromText(paramsString + " -> {}", null);
          lambdaExpression = (PsiLambdaExpression)codeStyleManager.reformat(lambdaExpression);
          paramsString = lambdaExpression.getParameterList().getText();
          final LookupElementBuilder builder =
            LookupElementBuilder.create(paramsString).withPresentableText(paramsString + " -> {}").withInsertHandler(new InsertHandler<LookupElement>() {
              @Override
              public void handleInsert(InsertionContext context, LookupElement item) {
                final Editor editor = context.getEditor();
                EditorModificationUtil.insertStringAtCaret(editor, " -> ");
              }
            });
          result.addElement(builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
        }
      }
    }
  }

  private static String getParamName(PsiParameter param, JavaCodeStyleManager javaCodeStyleManager, PsiElement originalPosition) {
    return javaCodeStyleManager.suggestUniqueVariableName(param.getName(), originalPosition, true);
  }
}
