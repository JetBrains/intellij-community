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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 */
public class FunctionalExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    result.addAllElements(getLambdaVariants(parameters, false));
  }

  static List<LookupElement> getLambdaVariants(@NotNull CompletionParameters parameters, boolean prioritize) {
    if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile())) return Collections.emptyList();

    List<LookupElement> result = ContainerUtil.newArrayList();
    for (ExpectedTypeInfo expectedType : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      final PsiType defaultType = expectedType.getDefaultType();
      if (LambdaUtil.isFunctionalType(defaultType)) {
        final PsiType functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
        final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        if (functionalInterfaceMethod != null) {
          PsiParameter[] params = new PsiParameter[0];
          final PsiElement originalPosition = parameters.getPosition();
          final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(functionalInterfaceMethod, PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
          if (!functionalInterfaceMethod.hasTypeParameters()) {
            params = functionalInterfaceMethod.getParameterList().getParameters();
            final Project project = functionalInterfaceMethod.getProject();
            final JVMElementFactory jvmElementFactory = JVMElementFactories.getFactory(originalPosition.getLanguage(), project);
            final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
            if (jvmElementFactory != null) {
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
              LookupElementBuilder.create(functionalInterfaceMethod, paramsString).withPresentableText(paramsString + " -> {}").withInsertHandler(new InsertHandler<LookupElement>() {
                @Override
                public void handleInsert(InsertionContext context, LookupElement item) {
                  final Editor editor = context.getEditor();
                  EditorModificationUtil.insertStringAtCaret(editor, " -> ");
                }
              }).withIcon(AllIcons.Nodes.AnonymousClass);
            LookupElement lambdaElement = builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
            if (prioritize) {
              lambdaElement = PrioritizedLookupElement.withPriority(lambdaElement, 1);
            }
            result.add(lambdaElement);
          }

          if (params.length == 1) {
            final PsiType expectedReturnType = substitutor.substitute(functionalInterfaceMethod.getReturnType());
            if (expectedReturnType != null) {
              final PsiClass paramClass = PsiUtil.resolveClassInClassTypeOnly(params[0].getType());
              if (paramClass != null && !paramClass.hasTypeParameters()) {
                final Set<String> visited = new HashSet<String>();
                for (PsiMethod psiMethod : paramClass.getAllMethods()) {
                  final PsiType returnType = psiMethod.getReturnType();
                  if (returnType != null && 
                      psiMethod.getParameterList().getParametersCount() == 0 &&
                      visited.add(psiMethod.getName()) &&
                      !psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
                      JavaResolveUtil.isAccessible(psiMethod, null, psiMethod.getModifierList(), originalPosition, null, null) &&
                      TypeConversionUtil.isAssignable(expectedReturnType, returnType)) {
                    LookupElement methodRefLookupElement = LookupElementBuilder
                      .create(psiMethod)
                      .withPresentableText(paramClass.getName() + "::" + psiMethod.getName())
                      .withInsertHandler(new InsertHandler<LookupElement>() {
                        @Override
                        public void handleInsert(InsertionContext context, LookupElement item) {
                          final int startOffset = context.getStartOffset();
                          final Document document = context.getDocument();
                          final PsiFile file = context.getFile();
                          document.insertString(startOffset, "::");
                          JavaCompletionUtil.insertClassReference(paramClass, file, startOffset);
                        }
                      })
                      .withIcon(AllIcons.Nodes.AnonymousClass)
                      .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
                    if (prioritize && psiMethod.getContainingClass() == paramClass) {
                      methodRefLookupElement = PrioritizedLookupElement.withPriority(methodRefLookupElement, 1);
                    }
                    result.add(methodRefLookupElement);
                  }
                }
              }
            }
          }
        }
      }
    }
    return result;
  }

  private static String getParamName(PsiParameter param, JavaCodeStyleManager javaCodeStyleManager, PsiElement originalPosition) {
    return javaCodeStyleManager.suggestUniqueVariableName(param.getName(), originalPosition, true);
  }
}
