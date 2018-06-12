// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for all JVM elements.
 * <p>
 * JVM element represents a compiled element from perspective of the JVM.
 */
public interface JvmElement extends PomTarget {

  /**
   * @return corresponding source element or {@code null} if no source element is available
   */
  @Nullable
  PsiElement getSourceElement();

  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return visitor.visitElement(this);
  }
}
