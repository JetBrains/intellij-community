// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

public final class JavaSourceRootType extends JpsElementTypeBase<JavaSourceRootProperties> implements JpsModuleSourceRootType<JavaSourceRootProperties> {
  public static final JavaSourceRootType SOURCE = new JavaSourceRootType(false);
  public static final JavaSourceRootType TEST_SOURCE = new JavaSourceRootType(true);

  private final boolean forTests;

  private JavaSourceRootType(boolean isForTests) {
    forTests = isForTests;
  }

  @Override
  public boolean isForTests() {
    return forTests;
  }

  @Override
  public @NotNull JavaSourceRootProperties createDefaultProperties() {
    return JpsJavaExtensionService.getInstance().createSourceRootProperties("");
  }

  @Override
  public String toString() {
    return "JavaSourceRootType(" +
           "forTests=" + forTests +
           ')';
  }
}
