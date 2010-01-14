/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 12-Aug-2008
 */
package com.intellij.rt.execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class CommandLineWrapper {

  public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
                                                IllegalAccessException, IOException, InstantiationException {

    final List urls = new ArrayList();
    final File file = new File(args[0]);
    final BufferedReader reader = new BufferedReader(new FileReader(file));
    try {
      while(reader.ready()) {
        final String fileName = reader.readLine();
        try {
          urls.add(new File(fileName).toURI().toURL());
        }
        catch (NoSuchMethodError e) {
          urls.add(new File(fileName).toURL());
        }
      }
    }
    finally {
      reader.close();
    }
    file.delete();
    String progClass = args[1];
    String[] progArgs = new String[args.length - 2];
    System.arraycopy(args, 2, progArgs, 0, progArgs.length);
    ClassLoader loader = new URLClassLoader((URL[])urls.toArray(new URL[urls.size()]), null);
    final String classloader = System.getProperty("java.system.class.loader");
    if (classloader != null) {
      try {
        loader = (ClassLoader)Class.forName(classloader).getConstructor(new Class[]{ClassLoader.class}).newInstance(new Object[]{loader});
      }
      catch (Exception e) {
        //leave URL class loader
      }
    }
    Class mainClass = loader.loadClass(progClass);
    Thread.currentThread().setContextClassLoader(loader);
    Class mainArgType = (new String[0]).getClass();
    Method main = mainClass.getMethod("main", new Class[]{mainArgType});
    ensureAccess(main);
    main.invoke(null, new Object[]{progArgs});
  }

   private static void ensureAccess(Object reflectionObject) {
    // need to call setAccessible here in order to be able to launch package-local classes
    // calling setAccessible() via reflection because the method is missing from java version 1.1.x
    final Class aClass = reflectionObject.getClass();
    try {
      final Method setAccessibleMethod = aClass.getMethod("setAccessible", new Class[] {boolean.class});
      setAccessibleMethod.invoke(reflectionObject, new Object[] {Boolean.TRUE});
    }
    catch (Exception e) {
      // the method not found
    }
  }
}
