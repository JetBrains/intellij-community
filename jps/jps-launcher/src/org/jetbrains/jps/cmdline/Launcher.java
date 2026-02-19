// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

@ApiStatus.Internal
public final class Launcher {
  public static void main(String[] args) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InstantiationException, InvocationTargetException {
    final String jpsClasspath = args[0];
    final String mainClassName = args[1];
    final String[] jpsArgs = new String[args.length - 2];
    System.arraycopy(args, 2, jpsArgs, 0, jpsArgs.length);

    final StringTokenizer tokenizer = new StringTokenizer(jpsClasspath, File.pathSeparator, false);
    final List<URL> urls = new ArrayList<>();
    while (tokenizer.hasMoreTokens()) {
      final String path = tokenizer.nextToken();
      urls.add(new File(path).toURI().toURL());
    }
    final URLClassLoader jpsLoader = new URLClassLoader(urls.toArray(new URL[0]), Launcher.class.getClassLoader());

    // IDEA-120811; speeding up DefaultChannelIDd calculation for netty
    //if (Boolean.parseBoolean(System.getProperty("io.netty.random.id"))) {
      System.setProperty("io.netty.machineId", "28:f0:76:ff:fe:16:65:0e");
      System.setProperty("io.netty.processId", Integer.toString(new Random().nextInt(65535)));
      System.setProperty("io.netty.serviceThreadPrefix", "Netty");
      System.setProperty("io.netty.allocator.type", "pooled");
    //}

    final Class<?> mainClass = jpsLoader.loadClass(mainClassName);
    final Method mainMethod = mainClass.getMethod("main", String[].class);
    Thread.currentThread().setContextClassLoader(jpsLoader);
    mainMethod.invoke(null, new Object[] {jpsArgs});
  }
}
