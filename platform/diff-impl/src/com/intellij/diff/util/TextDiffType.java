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
package com.intellij.diff.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface TextDiffType {
  @NotNull TextDiffType INSERTED = TextDiffTypeFactory.getInstance()
    .createTextDiffType(DiffColors.DIFF_INSERTED, DiffBundle.message("diff.type.inserted.name"));
  @NotNull TextDiffType DELETED = TextDiffTypeFactory.getInstance()
    .createTextDiffType(DiffColors.DIFF_DELETED, DiffBundle.message("diff.type.deleted.name"));
  @NotNull TextDiffType MODIFIED = TextDiffTypeFactory.getInstance()
    .createTextDiffType(DiffColors.DIFF_MODIFIED, DiffBundle.message("diff.type.changed.name"));
  @NotNull TextDiffType CONFLICT = TextDiffTypeFactory.getInstance()
    .createTextDiffType(DiffColors.DIFF_CONFLICT, DiffBundle.message("diff.type.conflict.name"));

  @NotNull
  String getName();

  @NotNull
  Color getColor(@Nullable Editor editor);

  @NotNull
  Color getIgnoredColor(@Nullable Editor editor);

  @Nullable
  Color getMarkerColor(@Nullable Editor editor);
}
