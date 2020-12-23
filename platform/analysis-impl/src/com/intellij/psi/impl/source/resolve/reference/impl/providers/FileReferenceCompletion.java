// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class FileReferenceCompletion {
  public static FileReferenceCompletion getInstance() {
    return ApplicationManager.getApplication().getService(FileReferenceCompletion.class);
  }

  public abstract Object @NotNull [] getFileReferenceCompletionVariants(FileReference reference);
}
