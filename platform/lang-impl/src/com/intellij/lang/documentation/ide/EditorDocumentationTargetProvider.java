// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide;

import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// TODO consider EP instead of a service override
@Internal
public interface EditorDocumentationTargetProvider {

  static @NotNull EditorDocumentationTargetProvider getInstance() {
    return ApplicationManager.getApplication().getService(EditorDocumentationTargetProvider.class);
  }

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(
    @NotNull Editor editor,
    @NotNull PsiFile file,
    int offset
  );
}
