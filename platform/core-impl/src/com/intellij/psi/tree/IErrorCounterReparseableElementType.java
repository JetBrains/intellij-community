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

package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class IErrorCounterReparseableElementType extends IReparseableElementType {
  public static final int NO_ERRORS = 0;
  public static final int FATAL_ERROR = Integer.MIN_VALUE;

  public IErrorCounterReparseableElementType(@NonNls String debugName, Language language) {
    super(debugName, language);
  }

  public abstract int getErrorsCount(CharSequence seq, Language fileLanguage, Project project);

  @Override
  public boolean isParsable(@NotNull CharSequence buffer, @NotNull Language fileLanguage, @NotNull Project project) {
    return getErrorsCount(buffer, fileLanguage, project) == NO_ERRORS;
  }
}
