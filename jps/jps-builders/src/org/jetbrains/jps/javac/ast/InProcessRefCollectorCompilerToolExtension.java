// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac.ast;

import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.service.JpsServiceManager;

public class InProcessRefCollectorCompilerToolExtension extends AbstractRefCollectorCompilerToolExtension {
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

  @Override
  protected boolean divideImportsRefs() {
    if (hasServiceManager()) {
      for (JavacFileReferencesRegistrar registrar : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
        if (registrar.isEnabled() && registrar.onlyImports()) {
          return true;
        }
      }
    }
    return false;
  }
}
