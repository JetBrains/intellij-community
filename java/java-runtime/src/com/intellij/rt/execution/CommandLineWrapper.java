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
package com.intellij.rt.execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 * @since 12-Aug-2008
 */
public class CommandLineWrapper {
  private static final String PREFIX = "-D";

  public static void main(String[] args) throws Exception {
    final List urls = new ArrayList();
    final File file = new File(args[0]);
    final StringBuffer buf = new StringBuffer();
    final BufferedReader reader = new BufferedReader(new FileReader(file));
    try {
      while(reader.ready()) {
        final String fileName = reader.readLine();
        if (buf.length() > 0) {
          buf.append(File.pathSeparator);
        }
        buf.append(fileName);
        try {
          //noinspection Since15
          urls.add(new File(fileName).toURI().toURL());
        }
        catch (NoSuchMethodError e) {
          //noinspection deprecation
          urls.add(new File(fileName).toURL());
        }
      }
    }
    finally {
      reader.close();
    }
    if (!file.delete()) file.deleteOnExit();
    System.setProperty("java.class.path", buf.toString());

    int startArgsIdx = 2;
    if (args[1].equals("@vm_params")) {
      startArgsIdx = 4;
      final File vmParamsFile = new File(args[2]);
      final BufferedReader vmParamsReader = new BufferedReader(new FileReader(vmParamsFile));
      try {
        while (vmParamsReader.ready()) {
          final String vmParam = vmParamsReader.readLine().trim();
          final int eqIdx = vmParam.indexOf('=');
          String vmParamName;
          String vmParamValue;

          if (eqIdx > -1 && eqIdx < vmParam.length() - 1) {
            vmParamName = vmParam.substring(0, eqIdx);
            vmParamValue = vmParam.substring(eqIdx + 1);
          } else {
            vmParamName = vmParam;
            vmParamValue = "";
          }
          vmParamName = vmParamName.trim();
          if (vmParamName.startsWith(PREFIX)) {
            vmParamName = vmParamName.substring(PREFIX.length());
            System.setProperty(vmParamName, vmParamValue);
          }
        }
      }
      finally {
        vmParamsReader.close();
      }
      if (!vmParamsFile.delete()) vmParamsFile.deleteOnExit();
    }

    String mainClassName = args[startArgsIdx - 1];
    String[] mainArgs = new String[args.length - startArgsIdx];
    System.arraycopy(args, startArgsIdx, mainArgs, 0, mainArgs.length);

    for (int i = 0; i < urls.size(); i++) {
      URL url = (URL)urls.get(i);
      urls.set(i, internFileProtocol(url));
    }

    ClassLoader loader = new URLClassLoader((URL[])urls.toArray(new URL[urls.size()]), null);
    final String classLoader = System.getProperty("java.system.class.loader");
    if (classLoader != null) {
      try {
        loader = (ClassLoader)Class.forName(classLoader).getConstructor(new Class[]{ClassLoader.class}).newInstance(new Object[]{loader});
      }
      catch (Exception e) {
        //leave URL class loader
      }
    }

    Class mainClass = loader.loadClass(mainClassName);
    Thread.currentThread().setContextClassLoader(loader);
    //noinspection SSBasedInspection
    Class mainArgType = (new String[0]).getClass();
    Method main = mainClass.getMethod("main", new Class[]{mainArgType});
    ensureAccess(main);
    main.invoke(null, new Object[]{mainArgs});
  }

  private static URL internFileProtocol(URL url) {
    try {
      if ("file".equals(url.getProtocol())) {
        return new URL("file", url.getHost(), url.getPort(), url.getFile());
      }
    }
    catch (MalformedURLException ignored) {
    }
    return url;
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
