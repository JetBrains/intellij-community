// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiReference;

/**
 * Interface for references contributed to Java String literals that may {@link PsiReference#resolve()} to Java class members
 * ({@link com.intellij.psi.PsiMember}) and should be taken into account as the member usage in the unused declaration inspection.
 */
public interface PsiMemberReference extends PsiReference {
}
