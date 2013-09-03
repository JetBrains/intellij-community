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
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
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
      if (LambdaHighlightingUtil.checkInterfaceFunctional(defaultType) == null) {
        final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(defaultType);
        if (method != null) {
          final PsiParameter[] params = method.getParameterList().getParameters();
          final String paramsString = "(" + StringUtil.join(params, new Function<PsiParameter, String>() {
            @Override
            public String fun(PsiParameter parameter) {
              return parameter.getName();
            }
          }, ",") + ")";
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
}
