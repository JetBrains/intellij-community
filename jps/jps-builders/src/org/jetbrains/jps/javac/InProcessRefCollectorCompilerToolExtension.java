// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.jps.service.JpsServiceManager;

public final class InProcessRefCollectorCompilerToolExtension extends AbstractRefCollectorCompilerToolExtension {
  @Override
  protected boolean isEnabled() {
    if (hasServiceManager()) {
      for (JavacFileReferencesRegistrar registrar : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
        if (registrar.isEnabled()) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasServiceManager() {
    try {
      @SuppressWarnings("unused")
      final Class<JpsServiceManager> jpsServiceManager = JpsServiceManager.class;
      return true;
    }
    catch (NoClassDefFoundError ignored) {
      return false;
    }
  }

}
