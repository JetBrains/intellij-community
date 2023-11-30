// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * User: anna
  */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class EntryPointsManager implements Disposable {
  public static EntryPointsManager getInstance(Project project) {
    return project.getService(EntryPointsManager.class);
  }

  public abstract void resolveEntryPoints(@NotNull RefManager manager);

  public abstract void addEntryPoint(@NotNull RefElement newEntryPoint, boolean isPersistent);

  public abstract void removeEntryPoint(@NotNull RefElement anEntryPoint);

  public abstract RefElement @NotNull [] getEntryPoints(@NotNull RefManager refManager);

  public abstract void cleanup();

  public abstract boolean isAddNonJavaEntries();

  /**
   * Show UI to configure entry points and implicitly written field annotations, if applicable
   */
  public abstract void configureAnnotations();

  /**
   * Show UI to configure entry points annotations, if applicable
   * @param implicitWritesOnly whether to configure implicitly written fields only (no entry points)
   */
  public void configureAnnotations(boolean implicitWritesOnly) {
    configureAnnotations();
  }

  public abstract boolean isEntryPoint(@NotNull PsiElement element);

  /**
   * Returns {@code true} for fields, annotated with "write" annotations
   */
  public abstract boolean isImplicitWrite(@NotNull PsiElement element);
}
