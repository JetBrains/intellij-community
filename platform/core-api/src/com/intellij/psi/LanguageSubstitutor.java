// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to change the language used to parse given file in the context of a specific project.<p/>
 *
 * When determining the file's final language, the IDE takes the language associated with its file type,
 * queries all {@link LanguageSubstitutor}s registered for that language, and returns the result
 * of the first one that returned a non-null value.
 *
 * @see LanguageSubstitutors
 */
public abstract class LanguageSubstitutor {

  /**
   * @return the language that should be used instead of the default one for the given file in the given project,
   * or null if this substitutor isn't applicable to this file.
   */
  public abstract @Nullable Language getLanguage(@NotNull VirtualFile file, @NotNull Project project);
}
