// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.lang;

import com.intellij.diff.contents.DiffContent;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class LangDiffIgnoredRangeProvider implements DiffIgnoredRangeProvider {
  protected abstract boolean accepts(@NotNull Project project, @NotNull Language language);

  protected abstract @NotNull List<TextRange> computeIgnoredRanges(@NotNull Project project, @NotNull CharSequence text, @NotNull Language language);

  @Override
  public final boolean accepts(@Nullable Project project, @NotNull DiffContent content) {
    if (project == null) return false;
    Language language = DiffLanguage.getLanguageOrCompute(project, content);
    if (language == null) return false;
    return accepts(project, language);
  }

  @Override
  public @NotNull List<TextRange> getIgnoredRanges(@Nullable Project project, @NotNull CharSequence text, @NotNull DiffContent content) {
    assert project != null;
    Language language = DiffLanguage.getLanguageOrCompute(project, content);
    assert language != null;

    return computeIgnoredRanges(project, text, language);
  }
}
