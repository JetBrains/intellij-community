// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.packaging.elements;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class ComplexPackagingElementType<E extends ComplexPackagingElement<?>> extends PackagingElementType<E> {
  protected ComplexPackagingElementType(@NotNull @NonNls String id,
                                        @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName) {
    super(id, presentableName);
  }

  public abstract @NlsActions.ActionText String getShowContentActionText();

  public @Nullable ModificationTracker getAllSubstitutionsModificationTracker(@NotNull Project project) {
    return null;
  }
}
