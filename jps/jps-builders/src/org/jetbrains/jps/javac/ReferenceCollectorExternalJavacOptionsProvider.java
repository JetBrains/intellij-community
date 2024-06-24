// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.incremental.java.ExternalJavacOptionsProvider;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
public final class ReferenceCollectorExternalJavacOptionsProvider implements ExternalJavacOptionsProvider {
  @Override
  public @NotNull Collection<String> getOptions(@NotNull JavaCompilingTool tool, int compilerSdkVersion) {
    if (tool.getId().equals(JavaCompilers.JAVAC_ID)) {
      return Collections.singletonList("-D" + ExternalRefCollectorCompilerToolExtension.ENABLED_PARAM + "=" + isEnabled());
    }
    return Collections.emptyList();
  }

  private static boolean isEnabled() {
    for (JavacFileReferencesRegistrar listener : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
      if (listener.isEnabled()) {
        return true;
      }
    }
    return false;
  }
}
