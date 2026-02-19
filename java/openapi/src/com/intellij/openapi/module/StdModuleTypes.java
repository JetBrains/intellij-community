// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;

/**
 * @deprecated it's better to avoid checking types of modules at all, see {@link ModuleType} for details; if you really need to access
 * the Java module type, use {@link JavaModuleType#getModuleType()} instead.
 */
@Deprecated(forRemoval = true)
public final class StdModuleTypes {
  /**
   * @deprecated it's better to avoid checking types of modules at all, see {@link ModuleType} for details; if you really need to access
   * the Java module type, use {@link JavaModuleType#getModuleType()} instead.
   */
  @Deprecated(forRemoval = true)
  public static final ModuleType<JavaModuleBuilder> JAVA;

  static {
    try {
      JAVA = new JavaModuleType();
    }
    catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
