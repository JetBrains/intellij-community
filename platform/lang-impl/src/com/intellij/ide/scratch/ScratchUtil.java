/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public class ScratchUtil {
  private ScratchUtil() {
  }

  /**
   * Returns true if a file is in one of scratch roots: scratch, console, etc.
   * @see RootType
   * @see ScratchFileService
   */
  public static boolean isScratch(@Nullable VirtualFile file) {
    return file != null && file.getFileType() == ScratchFileType.INSTANCE;
  }

  @Nullable
  public static Language getLanguage(@NotNull Project project, @Nullable VirtualFile file) {
    Language language = LanguageUtil.getFileLanguage(file);
    if (language == null) return null;
    return LanguageSubstitutors.INSTANCE.substituteLanguage(language, file, project);
  }
}
