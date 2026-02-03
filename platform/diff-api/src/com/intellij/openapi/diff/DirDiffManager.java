// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffModel;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class DirDiffManager {
  public static DirDiffManager getInstance(@NotNull Project project) {
    return project.getService(DirDiffManager.class);
  }

  public abstract void showDiff(@NotNull DiffElement dir1, @NotNull DiffElement dir2, DirDiffSettings settings, @Nullable Runnable onWindowClosing);

  public abstract void showDiff(@NotNull DiffElement dir1, @NotNull DiffElement dir2, DirDiffSettings settings);

  public abstract void showDiff(@NotNull DiffElement dir1, @NotNull DiffElement dir2);

  public abstract boolean canShow(@NotNull DiffElement dir1, @NotNull DiffElement dir2);

  public abstract @Nullable DiffElement createDiffElement(Object obj);

  public abstract DirDiffModel createDiffModel(DiffElement e1, DiffElement e2, DirDiffSettings settings);
}
