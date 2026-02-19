// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTestModuleProperties;

/**
 * Temporary interface which provides access to custom implementation of Java-related properties in JPS Model. 
 */
@ApiStatus.Internal
public interface JpsJavaAwareProject extends JpsProject {
  @Nullable JpsJavaModuleExtension getJavaModuleExtension(@NotNull JpsModule module);
  
  @Nullable JpsJavaDependencyExtension getJavaDependencyExtension(@NotNull JpsDependencyElement element);

  @Nullable JpsTestModuleProperties getTestModuleProperties(@NotNull JpsModule module);

  boolean isProductionOnTestDependency(@NotNull JpsDependencyElement element);
}
