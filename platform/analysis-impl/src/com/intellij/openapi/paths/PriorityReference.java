// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.paths;

import com.intellij.psi.PsiReferenceRegistrar;

public interface PriorityReference {
   default double getPriority() { return PsiReferenceRegistrar.DEFAULT_PRIORITY; }
}
