// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.lang;

import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DiffIgnoredRangeProvider {
  ExtensionPointName<DiffIgnoredRangeProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.diff.lang.DiffIgnoredRangeProvider");

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getDescription();

  boolean accepts(@Nullable Project project, @NotNull DiffContent content);

  @NotNull
  List<TextRange> getIgnoredRanges(@Nullable Project project, @NotNull CharSequence text, @NotNull DiffContent content);
}
