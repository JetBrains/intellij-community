// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Set;

public final class JavaModuleSourceRootTypes {
  public static final Set<JavaSourceRootType> SOURCES = ContainerUtil.newHashSet(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE);
  public static final Set<JavaResourceRootType> RESOURCES = ContainerUtil.newHashSet(JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> PRODUCTION = ContainerUtil.newHashSet(JavaSourceRootType.SOURCE, JavaResourceRootType.RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> TESTS = ContainerUtil.newHashSet(JavaSourceRootType.TEST_SOURCE, JavaResourceRootType.TEST_RESOURCE);
}
