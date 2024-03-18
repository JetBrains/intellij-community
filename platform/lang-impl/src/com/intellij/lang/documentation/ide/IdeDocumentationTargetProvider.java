// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

// This service exists for Rider and CWM to override.
// TODO consider EP instead of a service override
public interface IdeDocumentationTargetProvider {

  static @NotNull IdeDocumentationTargetProvider getInstance(@NotNull Project project) {
    return project.getService(IdeDocumentationTargetProvider.class);
  }

  @ApiStatus.OverrideOnly
  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  @SuppressWarnings("unused")
  default @Nullable DocumentationTarget documentationTarget(
    @NotNull Editor editor,
    @NotNull PsiFile file,
    @NotNull LookupElement lookupElement
  ) {
    throw new IllegalStateException("Override this or documentationTargets(Editor, PsiFile, LookupElement)");
  }

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  default @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(
    @NotNull Editor editor,
    @NotNull PsiFile file,
    @NotNull LookupElement lookupElement
  ) {
    DocumentationTarget target = documentationTarget(editor, file, lookupElement);
    return target == null ? Collections.emptyList() : List.of(target);
  }

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(
    @NotNull Editor editor,
    @NotNull PsiFile file,
    int offset
  );
}
