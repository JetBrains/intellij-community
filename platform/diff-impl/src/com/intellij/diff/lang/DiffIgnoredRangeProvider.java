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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DiffIgnoredRangeProvider {
  ExtensionPointName<DiffIgnoredRangeProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.diff.lang.DiffIgnoredRangeProvider");

  @NotNull
  String getDescription();

  boolean accepts(@Nullable Project project, @NotNull DiffContent content);

  @NotNull
  List<TextRange> getIgnoredRanges(@Nullable Project project, @NotNull CharSequence text, @NotNull DiffContent content);
}
