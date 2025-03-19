// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

import java.security.ProtectionDomain;

@Internal
public interface BytecodeTransformer {
  default boolean isApplicable(String className, ClassLoader loader, @Nullable ProtectionDomain protectionDomain) {
    return true;
  }

  byte[] transform(ClassLoader loader, String className, @Nullable ProtectionDomain protectionDomain, byte[] classBytes);
}
