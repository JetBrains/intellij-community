// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;

public abstract class CodeStyleSchemes {
  public static CodeStyleSchemes getInstance(){
    return ApplicationManager.getApplication().getService(CodeStyleSchemes.class);
  }

  public abstract CodeStyleScheme getCurrentScheme();

  public abstract void setCurrentScheme(CodeStyleScheme scheme);

  public abstract CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme);

  @TestOnly
  public abstract void deleteScheme(@NotNull CodeStyleScheme scheme);

  @Nullable
  public abstract CodeStyleScheme findSchemeByName(@NotNull String name);

  /**
   * Attempts to find a scheme with a given name or an alternative suitable scheme.
   *
   * @param preferredSchemeName The scheme name to find or null for the currently selected scheme.
   * @return A found scheme or a default scheme if the scheme name was not found or, if neither exists or the scheme name is null, the
   *         currently selected scheme.
   */
  @NotNull
  public CodeStyleScheme findPreferredScheme(@Nullable String preferredSchemeName) {
    CodeStyleScheme scheme = null;
    if (preferredSchemeName != null) {
      scheme = findSchemeByName(preferredSchemeName);
    }
    if (scheme == null) {
      scheme = getCurrentScheme();
    }
    if (scheme == null) {
      scheme = getDefaultScheme();
    }
    return scheme;
  }

  public abstract CodeStyleScheme getDefaultScheme();

  public abstract void addScheme(@NotNull CodeStyleScheme currentScheme);

  public List<CodeStyleScheme> getAllSchemes() {
    return Collections.emptyList();
  }
}

