/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class CodeFragmentFactory {
  public static final ExtensionPointName<CodeFragmentFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.debugger.codeFragmentFactory");

  public abstract JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, Project project);

  public abstract JavaCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project);

  public abstract boolean isContextAccepted(PsiElement contextElement);

  @NotNull
  public abstract LanguageFileType getFileType();

  /**
   * In case if createCodeFragment returns java code use
   * com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl#getInstance()
   * @return builder, which can evaluate expression for your code fragment
   */
  public abstract EvaluatorBuilder getEvaluatorBuilder();
}
