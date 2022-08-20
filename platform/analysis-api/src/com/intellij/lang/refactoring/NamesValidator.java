// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.refactoring;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates the knowledge about identifier rules and keyword set of the assigned language.
 * <p>
 * Register in {@code com.intellij.lang.namesValidator} extension point.
 * @see com.intellij.lang.LanguageNamesValidation
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/rename-refactoring.html">Rename Refactoring (IntelliJ Platform Docs)</a>
 */
public interface NamesValidator {
  /**
   * Checks if the specified string is a keyword in the custom language.
   *
   * @param name    the string to check
   * @param project the project in the context of which the check is done
   * @return {@code true} if the string is a keyword, {@code false} otherwise
   */
  boolean isKeyword(@NotNull String name, Project project);

  /**
   * Checks if the specified string is a valid identifier in the custom language.
   *
   * @param name    the string to check
   * @param project the project in the context of which the check is done
   * @return {@code true} if the string is a valid identifier, {@code false} otherwise
   */
  boolean isIdentifier(@NotNull String name, Project project);
}
