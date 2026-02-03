// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see java.lang.reflect.Member
 */
public interface JvmMember extends JvmModifiersOwner, JvmNamedElement {

  /**
   * @see java.lang.reflect.Member#getDeclaringClass
   */
  @Nullable
  JvmClass getContainingClass();

  /**
   * @see java.lang.reflect.Member#getName
   */
  @Nullable
  @Override
  String getName();

  @Override
  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return visitor.visitMember(this);
  }
}
