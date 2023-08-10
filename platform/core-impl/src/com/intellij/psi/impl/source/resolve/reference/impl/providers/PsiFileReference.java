// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiPolyVariantReference;

/**
 * A reference that is known to resolve to a PsiFile.
 */
public interface PsiFileReference extends PsiPolyVariantReference {
}
