// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
final class CorePsiDocumentManager extends PsiDocumentManagerBase {
  CorePsiDocumentManager(@NotNull Project project) {
    super(project);
  }
}
