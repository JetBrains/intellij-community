/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.cmdline;

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

/**
 * @author Eugene Zhuravlev
 */
public class Launcher {

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
    final URLClassLoader jpsLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Launcher.class.getClassLoader());
    
    // IDEA-120811; speeding up DefaultChannelIDd calculation for netty
    //if (Boolean.parseBoolean(System.getProperty("io.netty.random.id"))) {
      System.setProperty("io.netty.machineId", "28:f0:76:ff:fe:16:65:0e");
      System.setProperty("io.netty.processId", Integer.toString(new Random().nextInt(65535)));
      System.setProperty("io.netty.serviceThreadPrefix", "Netty");
    //}

    final Class<?> mainClass = jpsLoader.loadClass(mainClassName);
    final Method mainMethod = mainClass.getMethod("main", String[].class);
    Thread.currentThread().setContextClassLoader(jpsLoader);
    mainMethod.invoke(null, new Object[] {jpsArgs});
  }
}
