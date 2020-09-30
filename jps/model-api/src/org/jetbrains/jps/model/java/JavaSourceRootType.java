// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

public final class JavaSourceRootType extends JpsElementTypeBase<JavaSourceRootProperties> implements JpsModuleSourceRootType<JavaSourceRootProperties> {
  public static final JavaSourceRootType SOURCE = new JavaSourceRootType(false);
  public static final JavaSourceRootType TEST_SOURCE = new JavaSourceRootType(true);

  private final boolean myForTests;

  private JavaSourceRootType(boolean isForTests) {
    myForTests = isForTests;
  }

  @Override
  public boolean isForTests() {
    return myForTests;
  }

  @NotNull
  @Override
  public JavaSourceRootProperties createDefaultProperties() {
    return JpsJavaExtensionService.getInstance().createSourceRootProperties("");
  }
}
