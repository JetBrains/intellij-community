// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import org.jetbrains.annotations.Nullable;

import java.security.ProtectionDomain;

public interface BytecodeTransformer {
  default boolean isApplicable(String className, ClassLoader loader, @Nullable ProtectionDomain protectionDomain) {
    return true;
  }

  byte[] transform(ClassLoader loader, String className, @Nullable ProtectionDomain protectionDomain, byte[] classBytes);
}
