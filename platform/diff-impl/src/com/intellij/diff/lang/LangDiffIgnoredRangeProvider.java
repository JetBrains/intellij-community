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
package com.intellij.diff.lang;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class LangDiffIgnoredRangeProvider implements DiffIgnoredRangeProvider {
  protected abstract boolean accepts(@NotNull Project project, @NotNull Language language);

  @NotNull
  protected abstract List<TextRange> computeIgnoredRanges(@NotNull Project project, @NotNull CharSequence text, @NotNull Language language);

  @Override
  public final boolean accepts(@Nullable Project project, @NotNull DiffContent content) {
    if (project == null) return false;
    Language language = getLanguage(project, content);
    if (language == null) return false;
    return accepts(project, language);
  }

  @NotNull
  @Override
  public List<TextRange> getIgnoredRanges(@Nullable Project project, @NotNull CharSequence text, @NotNull DiffContent content) {
    assert project != null;
    Language language = getLanguage(project, content);
    assert language != null;

    return computeIgnoredRanges(project, text, language);
  }

  @Nullable
  private static Language getLanguage(@NotNull Project project, @NotNull DiffContent content) {
    Language language = content.getUserData(DiffUserDataKeys.LANGUAGE);
    if (language != null) return language;

    FileType type = content.getContentType();
    if (type instanceof LanguageFileType) language = ((LanguageFileType)type).getLanguage();

    if (language != null && content instanceof DocumentContent) {
      VirtualFile highlightFile = ((DocumentContent)content).getHighlightFile();
      if (highlightFile != null) language = LanguageSubstitutors.INSTANCE.substituteLanguage(language, highlightFile, project);
    }

    return language;
  }
}
