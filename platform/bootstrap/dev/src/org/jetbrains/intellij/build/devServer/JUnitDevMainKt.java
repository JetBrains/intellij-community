// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

// in java - don't use kotlin to avoid loading non-JDK classes
@ApiStatus.Internal
public final class JUnitDevMainKt {
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] rawArgs) throws Throwable {
    long start = System.currentTimeMillis();

    MethodHandles.Lookup lookup = MethodHandles.lookup();

    if (!(JUnitDevMainKt.class.getClassLoader() instanceof PathClassLoader classLoader)) {
      System.err.println("********************************************************************************************");
      System.err.println("* The current class loader is not a com.intellij.util.lang.PathClassLoader.                *");
      System.err.println("* This may mean that the \"Plugin DevKit\" IntelliJ IDEA plugin is outdated or absent.     *");
      System.err.println("* Please make sure you have the latest version of the Plugin DevKit installed and enabled. *");
      System.err.println("********************************************************************************************");
      return;
    }

    String jUnitStarterModule = findJUnitStarter();

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");
    System.setProperty("idea.build.pack.test.source.enabled", "true");
    if (jUnitStarterModule != null) System.setProperty("idea.dev.build.unpacked", "true");
    // idea.platform.prefix should be set
    if (System.getProperty("idea.platform.prefix") == null) {
      System.err.println("'idea.platform.prefix' is not set");
      System.exit(1);
    }

    // idea.dev.build.test.entry.point.module should be set
    if (System.getProperty("idea.dev.build.test.entry.point.module") == null) {
      System.err.println("'idea.dev.build.test.entry.point.module' is not set");
      System.exit(1);
    }
    // idea.dev.build.test.entry.point.class should be set
    if (jUnitStarterModule != null) {  // IDE
      System.setProperty("idea.dev.build.test.entry.point.class", "com.intellij.rt.junit.JUnitStarter");
    }
    else if (System.getProperty("idea.dev.build.test.entry.point.class") == null) {
      System.err.println("'idea.dev.build.test.entry.point.class' is not set");
      System.exit(1);
    }
    if (jUnitStarterModule != null) System.setProperty("idea.dev.build.test.additional.modules", jUnitStarterModule);

    // additional.modules should be set
    if (System.getProperty("additional.modules") == null) {
      System.err.println("'additional.modules' is not set");
      System.exit(1);
    }

    // separate method to not retain local variables like implClass
    if (!build(lookup, classLoader)) {
      // Unable to build the classpath: terminate.
      System.exit(1);
    }

    System.out.println("build completed in " + (System.currentTimeMillis() - start) + "ms");

    Class<?> mainClass = classLoader.loadClass("com.intellij.idea.TestMain");
    //noinspection ConfusingArgumentToVarargsMethod
    lookup.findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class)).invokeExact(rawArgs);
  }

  private static boolean build(MethodHandles.Lookup lookup, PathClassLoader classLoader) throws Throwable {
    // do not use classLoader as a parent - make sure that we don't make the initial classloader dirty
    // (say, do not load kotlin coroutine classes)
    Class<?> implClass = new PathClassLoader(UrlClassLoader.buildAsSystemClassLoader(classLoader.getFiles())
                                               .parent(ClassLoader.getPlatformClassLoader()))
      .loadClass("org.jetbrains.intellij.build.devServer.DevMainImpl");

    @SuppressWarnings("unchecked")
    Collection<Path> newClassPath =
      ((SimpleImmutableEntry<String, Collection<Path>>)
         lookup.findStatic(implClass, "buildDevMain", MethodType.methodType(SimpleImmutableEntry.class)).invokeExact())
      .getValue();

    classLoader.reset(newClassPath);
    return true;
  }

  private static String findJUnitStarter() {
    String testEntryPointClasspath = System.getProperty("idea.dev.build.test.entry.point.classpath");
    if (testEntryPointClasspath == null) {
      return null;
    }

    String result = null;

    List<String> versions = Arrays.asList("", "5", "6");
    for (String version : versions) {
      String junitRtJar = File.separator + "junit" + version + "-rt.jar";
      String junitRtPackage = File.separator + "intellij.junit" + (version.isEmpty() ? "" : ".v" + version) + ".rt";
      for (String s : testEntryPointClasspath.split(File.pathSeparator)) {
        if (s.endsWith(junitRtJar) || s.endsWith(junitRtPackage)) {
          result = result == null ? s : result + File.pathSeparator + s;
          break;
        }
      }
    }

    return result;
  }
}
