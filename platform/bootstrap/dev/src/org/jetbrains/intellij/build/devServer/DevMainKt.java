// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

import com.intellij.util.lang.PathClassLoader;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collection;

// in java - don't use kotlin to avoid loading non-JDK classes
public final class DevMainKt {

  private DevMainKt() { }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] rawArgs) throws Throwable {
    long start = System.currentTimeMillis();

    MethodHandles.Lookup lookup = MethodHandles.lookup();

    if (!(DevMainKt.class.getClassLoader() instanceof PathClassLoader classLoader)) {
      System.err.println("********************************************************************************************");
      System.err.println("* The current class loader is not a com.intellij.util.lang.PathClassLoader.                *");
      System.err.println("* This may mean that the \"Plugin DevKit\" IntelliJ IDEA plugin is outdated or absent.       *");
      System.err.println("* Please make sure you have the latest version of the Plugin DevKit installed and enabled. *");
      System.err.println("********************************************************************************************");
      return;
    }

    // separate method to not retain local variables like implClass
    String mainClassName = build(lookup, classLoader);

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");

    //noinspection UseOfSystemOutOrSystemErr
    System.out.println("build completed in " + (System.currentTimeMillis() - start) + "ms");

    Class<?> mainClass = classLoader.loadClass(mainClassName);
    //noinspection ConfusingArgumentToVarargsMethod
    lookup.findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class)).invokeExact(rawArgs);
  }

  private static String build(MethodHandles.Lookup lookup, PathClassLoader classLoader) throws Throwable {
    AbstractMap.SimpleImmutableEntry<String, Collection<Path>> mainClassAndClassPath;

    // do not use classLoader as a parent - make sure that we don't make the initial classloader dirty
    // (say, do not load kotlin coroutine classes)
    // also close the temporary classloader to unlock output jars on Windows
    try (URLClassLoader tempClassLoader = new URLClassLoader(classLoader.getUrls().toArray(URL[]::new),
                                                             ClassLoader.getPlatformClassLoader())) {
      Class<?> implClass = tempClassLoader.loadClass("org.jetbrains.intellij.build.devServer.DevMainImpl");

      //noinspection unchecked
      mainClassAndClassPath = (AbstractMap.SimpleImmutableEntry<String, Collection<Path>>)
        lookup
          .findStatic(implClass, "buildDevMain", MethodType.methodType(AbstractMap.SimpleImmutableEntry.class))
          .invokeExact();
    }

    classLoader.reset(mainClassAndClassPath.getValue());
    return mainClassAndClassPath.getKey();
  }
}
