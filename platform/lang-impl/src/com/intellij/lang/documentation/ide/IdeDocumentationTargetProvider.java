// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// This service exists for Rider and CWM to override.
// TODO consider EP instead of a service override
@Internal
public interface IdeDocumentationTargetProvider {

  static @NotNull IdeDocumentationTargetProvider getInstance(@NotNull Project project) {
    return project.getService(IdeDocumentationTargetProvider.class);
  }

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  @Nullable DocumentationTarget documentationTarget(
    @NotNull Editor editor,
    @NotNull PsiFile file,
    @NotNull LookupElement lookupElement
  );

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(
    @NotNull Editor editor,
    @NotNull PsiFile file,
    int offset
  );
}
