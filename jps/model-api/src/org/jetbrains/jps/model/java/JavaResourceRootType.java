// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

public final class JavaResourceRootType extends JpsElementTypeBase<JavaResourceRootProperties> implements
                                                                                         JpsModuleSourceRootType<JavaResourceRootProperties> {
  public static final JavaResourceRootType RESOURCE = new JavaResourceRootType(false);
  public static final JavaResourceRootType TEST_RESOURCE = new JavaResourceRootType(true);

  private final boolean myForTests;

  private JavaResourceRootType(boolean isForTests) {
    myForTests = isForTests;
  }

  @Override
  public boolean isForTests() {
    return myForTests;
  }

  @Override
  public @NotNull JavaResourceRootProperties createDefaultProperties() {
    return JpsJavaExtensionService.getInstance().createResourceRootProperties("", false);
  }

  @Override
  public String toString() {
    return "JavaResourceRootType(forTests=" + myForTests + ")";
  }
}
