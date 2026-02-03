// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Set;

public final class JavaModuleSourceRootTypes {
  public static final Set<JavaSourceRootType> SOURCES = Set.of(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE);
  public static final Set<JavaResourceRootType> RESOURCES = Set.of(JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> PRODUCTION = Set.of(JavaSourceRootType.SOURCE, JavaResourceRootType.RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> TESTS = Set.of(JavaSourceRootType.TEST_SOURCE, JavaResourceRootType.TEST_RESOURCE);
}
