// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public final class JavaBuilderExtensionImpl extends JavaBuilderExtension {
  @Override
  public boolean shouldHonorFileEncodingForCompilation(@NotNull File file) {
    return JavaBuilder.JAVA_SOURCES_FILTER.accept(file);
  }

  @Override
  public @NotNull Set<? extends JpsModuleType<?>> getCompilableModuleTypes() {
    return Collections.singleton(JpsJavaModuleType.INSTANCE);
  }
}
