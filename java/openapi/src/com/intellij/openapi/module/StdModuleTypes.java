// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;

public final class StdModuleTypes {
  public static final ModuleType<JavaModuleBuilder> JAVA;

  static {
    try {
      //noinspection unchecked
      JAVA = (ModuleType<JavaModuleBuilder>)Class.forName("com.intellij.openapi.module.JavaModuleType").newInstance();
    }
    catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
