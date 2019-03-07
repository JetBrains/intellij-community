// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a package access control directive ({@code exports} or {@code opens}) of a Java module declaration.
 */
public interface PsiPackageAccessibilityStatement extends PsiStatement {
  PsiPackageAccessibilityStatement[] EMPTY_ARRAY = new PsiPackageAccessibilityStatement[0];

  enum Role {EXPORTS, OPENS}

  @NotNull Role getRole();

  @Nullable PsiJavaCodeReferenceElement getPackageReference();
  @Nullable String getPackageName();

  @NotNull Iterable<PsiJavaModuleReferenceElement> getModuleReferences();
  @NotNull List<String> getModuleNames();
}