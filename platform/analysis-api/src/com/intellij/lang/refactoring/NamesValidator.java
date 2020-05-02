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
package com.intellij.lang.refactoring;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Instances of NamesValidator are obtained from {@link com.intellij.lang.Language} instance.
 * An instance encapsulates knowledge of identifier rules and keyword set of the language.
 */
public interface NamesValidator {
  /**
   * Checks if the specified string is a keyword in the custom language.
   *
   * @param name    the string to check.
   * @param project the project in the context of which the check is done.
   * @return true if the string is a keyword, false otherwise.
   */
  boolean isKeyword(@NotNull String name, Project project);

  /**
   * Checks if the specified string is a valid identifier in the custom language.
   *
   * @param name    the string to check.
   * @param project the project in the context of which the check is done.
   * @return true if the string is a valid identifier, false otherwise.
   */
  boolean isIdentifier(@NotNull String name, Project project);
}