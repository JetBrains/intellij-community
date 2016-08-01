/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.io.FileInputStream;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
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
    final MainPair mainPair = args[0].endsWith(".jar") ? loadMainClassFromClasspathJar(jarFile, args) 
                                                       : loadMainClassWithOldCustomLoader(jarFile, args);
    String[] mainArgs = mainPair.getArgs();
    Class mainClass = mainPair.getMainClass();
    //noinspection SSBasedInspection
    Class mainArgType = (new String[0]).getClass();
    Method main = mainClass.getMethod("main", new Class[]{mainArgType});
    ensureAccess(main);
    main.invoke(null, new Object[]{mainArgs});
  }

  private static MainPair loadMainClassFromClasspathJar(File jarFile, String[] args) throws Exception {
    String[] mainArgs;
    final JarInputStream inputStream = new JarInputStream(new FileInputStream(jarFile));
    try {
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
      String programParameters = manifest.getMainAttributes().getValue("Program-Parameters");
      if (programParameters == null) {
        mainArgs = new String[args.length - 2];
        System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);
      }
      else {
        mainArgs = splitBySpaces(programParameters);
      }
    }
    finally {
      if (inputStream != null) {
        inputStream.close();
      }
      jarFile.deleteOnExit();
    }

    return new MainPair(Class.forName(args[1]), mainArgs);
  }

  /**
   * The implementation is copied from copied from com.intellij.util.execution.ParametersListUtil.parse and adapted to old Java versions
   */
  private static String[] splitBySpaces(String parameterString) {
    parameterString = parameterString.trim();

    final ArrayList params = new ArrayList();
    final StringBuffer token = new StringBuffer(128);
    boolean inQuotes = false;
    boolean escapedQuote = false;
    boolean nonEmpty = false;

    for (int i = 0; i < parameterString.length(); i++) {
      final char ch = parameterString.charAt(i);

      if (ch == '\"') {
        if (!escapedQuote) {
          inQuotes = !inQuotes;
          nonEmpty = true;
          continue;
        }
        escapedQuote = false;
      }
      else if (Character.isWhitespace(ch)) {
        if (!inQuotes) {
          if (token.length() > 0 || nonEmpty) {
            params.add(token.toString());
            token.setLength(0);
            nonEmpty = false;
          }
          continue;
        }
      }
      else if (ch == '\\') {
        if (i < parameterString.length() - 1 && parameterString.charAt(i + 1) == '"') {
          escapedQuote = true;
          continue;
        }
      }

      token.append(ch);
    }

    if (token.length() > 0 || nonEmpty) {
      params.add(token.toString());
    }

    //noinspection SSBasedInspection
    return (String[])params.toArray(new String[params.size()]);
  }

  private static class MainPair {
    private Class mainClass;
    private String[] args;

    public MainPair(Class mainClass, String[] args) {
      this.mainClass = mainClass;
      this.args = args;
    }

    public Class getMainClass() {
      return mainClass;
    }

    public String[] getArgs() {
      return args;
    }
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
    // need to call setAccessible here in order to be able to launch package-private classes
    // calling setAccessible() via reflection because the method is missing from java version 1.1.x
    final Class aClass = reflectionObject.getClass();
    try {
      final Method setAccessibleMethod = aClass.getMethod("setAccessible", new Class[]{boolean.class});
      setAccessibleMethod.invoke(reflectionObject, new Object[]{Boolean.TRUE});
    }
    catch (Exception e) {
      // the method not found
    }
  }

  //todo delete; but new idea won't run correctly tests which start process with CommandLineWrapper
  private static MainPair loadMainClassWithOldCustomLoader(File file, String[] args) throws Exception {
    final List urls = new ArrayList();
    final StringBuffer buf = new StringBuffer();
    final BufferedReader reader = new BufferedReader(new FileReader(file));
    try {
      while (reader.ready()) {
        final String fileName = reader.readLine();
        if (buf.length() > 0) {
          buf.append(File.pathSeparator);
        }
        buf.append(fileName);
        File classpathElement = new File(fileName);
        try {
          //noinspection Since15, deprecation
          urls.add(classpathElement.toURI().toURL());
        }
        catch (NoSuchMethodError e) {
          //noinspection deprecation
          urls.add(classpathElement.toURL());
        }
      }
    }
    finally {
      reader.close();
    }
    if (!file.delete()) file.deleteOnExit();
    System.setProperty("java.class.path", buf.toString());

    int startArgsIdx = 2;

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

    return new MainPair(mainClass, mainArgs);
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
}
