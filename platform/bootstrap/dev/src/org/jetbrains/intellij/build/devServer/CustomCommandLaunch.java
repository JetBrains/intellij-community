// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Map;

/**
 * The custom command branch of {@link PreBuiltDevMain}, as {@code DevMainImpl} has it for a layout built at launch.
 * <p>
 * The first program argument names a {@code customCommands} entry of {@code product-info.json}. The entry gives the main
 * class and the JVM arguments the launch swaps in. Only a launch with {@code -Didea.dev.mode.custom.command=true} reads it.
 */
final class CustomCommandLaunch {
  private static final String PROPERTY = "idea.dev.mode.custom.command";

  private CustomCommandLaunch() { }

  static boolean isRequested() {
    return Boolean.getBoolean(PROPERTY);
  }

  /**
   * The main class and the system properties of the command {@code args[0]} names, read by
   * {@code BuildServerKt.readCustomCommandLaunch} in the distribution at {@code homePath}.
   */
  static Map.Entry<String, Map<String, String>> read(MethodHandles.Lookup lookup, Class<?> buildServer, Path homePath, String[] args)
    throws Throwable {
    if (args.length == 0) {
      throw new IllegalArgumentException("-D" + PROPERTY + "=true needs the command as the first program argument");
    }
    MethodHandle readCustomCommandLaunch =
      lookup.findStatic(buildServer, "readCustomCommandLaunch", MethodType.methodType(Map.Entry.class, Path.class, String.class));
    //noinspection unchecked
    return (Map.Entry<String, Map<String, String>>)readCustomCommandLaunch.invoke(homePath, args[0]);
  }
}
