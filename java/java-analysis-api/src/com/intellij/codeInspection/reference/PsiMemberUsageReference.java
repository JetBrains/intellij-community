// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiReference;

/**
 * Interfaces for references that may {@link PsiReference#resolve()} to Java class members ({@link com.intellij.psi.PsiMember}) and should
 * be counted as the member usage in unused declaration inspection.
 */
public interface PsiMemberUsageReference extends PsiReference {
}
