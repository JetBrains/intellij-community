// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Collection;

// in java - don't use kotlin to avoid loading non-JDK classes
public final class DevMainKt {
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
    if (!build(lookup, classLoader)) {
      // Unable to build the classpath: terminate.
      return;
    }

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");

    //noinspection UseOfSystemOutOrSystemErr
    System.out.println("build completed in " + (System.currentTimeMillis() - start) + "ms");

    Class<?> mainClass = classLoader.loadClass("com.intellij.idea.Main");
    //noinspection ConfusingArgumentToVarargsMethod
    lookup.findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class)).invokeExact(rawArgs);
  }

  private static boolean build(MethodHandles.Lookup lookup, PathClassLoader classLoader) throws Throwable {
    // do not use classLoader as a parent - make sure that we don't make the initial classloader dirty
    // (say, do not load kotlin coroutine classes)
    Class<?> implClass = new PathClassLoader(UrlClassLoader.build()
                                               .files(classLoader.getFiles())
                                               .parent(ClassLoader.getPlatformClassLoader()))
      .loadClass("org.jetbrains.intellij.build.devServer.DevMainImpl");

    @SuppressWarnings("unchecked")
    Collection<Path> newClassPath = (Collection<Path>)lookup
      .findStatic(implClass, "buildDevMain", MethodType.methodType(Collection.class))
      .invokeExact();

    classLoader.reset(newClassPath);
    return true;
  }
}
