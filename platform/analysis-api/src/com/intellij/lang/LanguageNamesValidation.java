// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LanguageNamesValidation extends LanguageExtension<NamesValidator> {
  public static final LanguageNamesValidation INSTANCE = new LanguageNamesValidation();

  private LanguageNamesValidation() {
    super("com.intellij.lang.namesValidator", new DefaultNamesValidator());
  }

  @Override
  @NotNull
  public NamesValidator forLanguage(@NotNull Language l) {
    return super.forLanguage(l);
  }

  protected static class DefaultNamesValidator implements NamesValidator {
    @Override
    public boolean isIdentifier(@NotNull final String name, final Project project) {
      final int len = name.length();
      if (len == 0) return false;

      if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;

      for (int i = 1; i < len; i++) {
        if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
      }

      return true;
    }

    @Override
    public boolean isKeyword(@NotNull final String name, final Project project) {
      return false;
    }
  }

  public static boolean isIdentifier(@NotNull Language language, @NotNull String name) {
    return isIdentifier(language, name, null);
  }

  public static boolean isIdentifier(@NotNull Language language, @NotNull String name, @Nullable Project project) {
    return INSTANCE.forLanguage(language).isIdentifier(name, project);
  }
}