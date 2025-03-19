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
package com.intellij.spellchecker;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.codeInsight.DumbAwareAnnotationUtil;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author shkate@jetbrains.com
 */
public class MethodNameTokenizerJava extends NamedElementTokenizer<PsiMethod> {

  @Override
  public void tokenize(@NotNull PsiMethod element, @NotNull TokenConsumer consumer) {
    if (element.isConstructor() ||
        (!DumbService.isDumb(element.getProject()) && element.findDeepestSuperMethods().length > 0) ||
        DumbAwareAnnotationUtil.hasAnnotation(element, CommonClassNames.JAVA_LANG_OVERRIDE)) {
      return;
    }
    super.tokenize(element, consumer);
  }
}
