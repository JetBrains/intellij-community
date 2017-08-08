/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CopyAbstractMethodImplementationAction extends ImplementAbstractMethodAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return "Copy abstract method implementation";
  }

  @Override
  protected String getIntentionName(final PsiMethod method) {
    return CodeInsightBundle.message("copy.abstract.method.intention.name", method.getName());
  }

  @Override
  protected boolean isAvailable(final MyElementProcessor processor) {
    return processor.hasMissingImplementations() && processor.hasExistingImplementations();
  }

  @Override
  protected void invokeHandler(final Project project, final Editor editor, final PsiMethod method) {
    new CopyAbstractMethodImplementationHandler(project, editor, method).invoke();
  }
}
