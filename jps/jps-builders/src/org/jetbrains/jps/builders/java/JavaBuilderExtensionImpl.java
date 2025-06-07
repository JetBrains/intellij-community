// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.io.File;
import java.util.Set;

@ApiStatus.Internal
public final class JavaBuilderExtensionImpl extends JavaBuilderExtension {
  private static final Set<JpsJavaModuleType> JAVA_MODULE_TYPES = Set.of(JpsJavaModuleType.INSTANCE);

  @Override
  public boolean shouldHonorFileEncodingForCompilation(@NotNull File file) {
    return JavaBuilder.JAVA_SOURCES_FILTER.accept(file);
  }

  @Override
  public @NotNull Set<? extends JpsModuleType<?>> getCompilableModuleTypes() {
    return JAVA_MODULE_TYPES;
  }
}
