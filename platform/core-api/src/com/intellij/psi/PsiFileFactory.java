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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsiFileFactory {
  public static Key<PsiFile> ORIGINAL_FILE = Key.create("ORIGINAL_FILE");
  public static PsiFileFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiFileFactory.class);
  }

  /**
   * Creates a file from the specified text.
   *
   * @param name the name of the file to create (the extension of the name determines the file type).
   * @param text the text of the file to create.
   * @return the created file.
   * @throws com.intellij.util.IncorrectOperationException if the file type with specified extension is binary.
   */
  @NotNull
  public abstract PsiFile createFileFromText(@NotNull @NonNls String name, @NotNull @NonNls String text);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String fileName, @NotNull FileType fileType, @NotNull CharSequence text);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                      long modificationStamp, boolean physical);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                      long modificationStamp, boolean physical, boolean markAsCopy);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                             boolean physical, boolean markAsCopy);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                             boolean physical, boolean markAsCopy, boolean noSizeLimit);

  public abstract PsiFile createFileFromText(FileType fileType, String fileName, CharSequence chars, int startOffset, int endOffset);

  @Nullable
  public abstract PsiFile createFileFromText(@NotNull CharSequence chars, @NotNull PsiFile original);  
}