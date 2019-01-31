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

package com.intellij.testIntegration;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Implementations of this extension are used on generating tests while navigation using GotoTestOrCodeAction. 
 * Corresponding extension point qualified name is {@code com.intellij.testCreator}, see {@link com.intellij.testIntegration.LanguageTestCreators}
 * <p>
 * To decorate creating test action consider implementing {@link ItemPresentation}
 */
public interface TestCreator {

  /**
   * Should "Create Test" action be available in this context?
   */
  boolean isAvailable(Project project, Editor editor, PsiFile file);

  /**
   * Triggered when the user actually selects the "Create New Test..." action from the popup. Responsible for actually creating the test file.
   */
  void createTest(Project project, Editor editor, PsiFile file);
}
