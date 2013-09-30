/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import java.util.StringTokenizer;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/27/13
 */
public class Launcher {

  public static void main(String[] args) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InstantiationException, InvocationTargetException {
    final String jpsClasspath = args[0];
    final String mainClassName = args[1];
    final String[] jpsArgs = new String[args.length - 2];
    System.arraycopy(args, 2, jpsArgs, 0, jpsArgs.length);
    
    final StringTokenizer tokenizer = new StringTokenizer(jpsClasspath, File.pathSeparator, false);
    final List<URL> urls = new ArrayList<URL>();
    while (tokenizer.hasMoreTokens()) {
      final String path = tokenizer.nextToken();
      urls.add(new File(path).toURI().toURL());
    }
    final URLClassLoader jpsLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Launcher.class.getClassLoader());
    
    final Class<?> mainClass = jpsLoader.loadClass(mainClassName);
    final Method mainMethod = mainClass.getMethod("main", String[].class);
    Thread.currentThread().setContextClassLoader(jpsLoader);
    mainMethod.invoke(null, new Object[] {jpsArgs});
  }
}
