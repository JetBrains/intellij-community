// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

import com.intellij.util.lang.PathClassLoader;
import org.jetbrains.annotations.ApiStatus;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

@ApiStatus.Internal
public final class BeforeRunDevMain {
  private static final String BEFORE_RUN_MAIN_CLASS_PROPERTY = "intellij.build.dev.server.before.run.main.class";

  private BeforeRunDevMain() { }

  static void main(String[] rawArgs) throws Throwable {
    var beforeRunMainClass = System.getProperty(BEFORE_RUN_MAIN_CLASS_PROPERTY);
    if (beforeRunMainClass == null || beforeRunMainClass.isBlank()) {
      throw new IllegalStateException("System property '" + BEFORE_RUN_MAIN_CLASS_PROPERTY + "' is not set");
    }

    if (!(BeforeRunDevMain.class.getClassLoader() instanceof PathClassLoader classLoader)) {
      throw new IllegalStateException("The current class loader is not a PathClassLoader");
    }

    try (var tempClassLoader = new URLClassLoader(classLoader.getUrls().toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
      var mainClass = tempClassLoader.loadClass(beforeRunMainClass);
      MethodHandles.publicLookup()
        .findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class))
        .invokeExact(rawArgs);
    }

    DevMainKt.main(rawArgs);
  }
}
