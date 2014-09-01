/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiNameHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Default NamesValidator interface implementation. Uses java language keyword set and java language rules for identifier.
 */
public class JavaNamesValidator implements NamesValidator {
  @Override
  public boolean isKeyword(@NotNull String name, Project project) {
    return PsiNameHelper.getInstance(project).isKeyword(name);
  }

  @Override
  public boolean isIdentifier(@NotNull String name, Project project) {
    return PsiNameHelper.getInstance(project).isIdentifier(name);
  }
}
