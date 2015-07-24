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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * @author anna
 * @since 12-Aug-2008
 */
public class CommandLineWrapper {

  private static final String PREFIX = "-D";

  public static void main(String[] args) throws Exception {
    final File jarFile = new File(args[0]);
    JarInputStream inputStream = null;
    try {
      inputStream = new JarInputStream(new FileInputStream(jarFile));
      final Manifest manifest = inputStream.getManifest();
      final String vmParams = manifest.getMainAttributes().getValue("VM-Options");
      if (vmParams != null) {
        final HashMap vmOptions = new HashMap();
        parseVmOptions(vmParams, vmOptions);
        for (Iterator iterator = vmOptions.keySet().iterator(); iterator.hasNext(); ) {
          String optionName = (String)iterator.next();
          System.setProperty(optionName, (String)vmOptions.get(optionName));
        }
      }
    }
    catch (IOException ignore) {}
    finally {
      if (inputStream != null) {
        inputStream.close();
      }
      jarFile.deleteOnExit();
    }
    
    String mainClassName = args[1];
    String[] mainArgs = new String[args.length - 2];
    System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);
    Class mainClass = Class.forName(mainClassName);
    //noinspection SSBasedInspection
    Class mainArgType = (new String[0]).getClass();
    Method main = mainClass.getMethod("main", new Class[]{mainArgType});
    ensureAccess(main);
    main.invoke(null, new Object[]{mainArgs});
  }

  public static void parseVmOptions(String vmParams, Map vmOptions) {
    int idx = vmParams.indexOf(PREFIX);
    while (idx >= 0) {
      final int indexOf = vmParams.indexOf(PREFIX, idx + PREFIX.length());
      final String vmParam = indexOf < 0 ? vmParams.substring(idx) : vmParams.substring(idx, indexOf - 1);
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
      vmOptions.put(vmParamName.trim().substring(PREFIX.length()), vmParamValue);
      idx = indexOf;
    }
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
