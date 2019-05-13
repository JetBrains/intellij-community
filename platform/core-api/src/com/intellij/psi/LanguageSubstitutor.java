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
  @Nullable
  public abstract Language getLanguage(@NotNull VirtualFile file, @NotNull Project project);
}
