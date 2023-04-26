// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

import com.intellij.idea.Main;
import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Collection;

// in java - don't use kotlin to avoid loading non-JDK classes
public class DevMainKt {
  public static void main(String[] rawArgs) throws Throwable {
    // separate method to not retain local variables like implClass
    build();

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");

    Main.main(rawArgs);
  }

  private static void build() throws Throwable {
    PathClassLoader classLoader = (PathClassLoader)DevMainKt.class.getClassLoader();
    Class<?> implClass = new PathClassLoader(UrlClassLoader.build().files(classLoader.getFiles()).parent(classLoader))
      .loadClass("org.jetbrains.intellij.build.devServer.DevMainImpl");

    @SuppressWarnings("unchecked")
    Collection<Path> newClassPath = (Collection<Path>)MethodHandles.lookup()
      .findStatic(implClass, "build", MethodType.methodType(Collection.class))
      .invokeExact();

    classLoader.reset(newClassPath);
  }
}

