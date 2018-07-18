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
package com.intellij.rt.execution;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * @author anna
 * @since 12-Aug-2008
 * @noinspection SSBasedInspection
 */
public class CommandLineWrapper {
  private static class AppData {
    private final List properties;
    private final Class mainClass;
    private final String[] args;

    private AppData(List properties, Class mainClass, String[] args) {
      this.properties = properties;
      this.mainClass = mainClass;
      this.args = args;
    }
  }

  public static void main(String[] args) throws Exception {
    File file = new File(args[0]);
    AppData appData = args[0].endsWith(".jar") ? loadMainClassFromClasspathJar(file, args) : loadMainClassWithCustomLoader(file, args);

    List properties = appData.properties;
    for (int i = 0; i < properties.size(); i++) {
      String property = (String)properties.get(i);
      if (property.startsWith("-D")) {
        int p = property.indexOf('=');
        if (p > 0) {
          System.setProperty(property.substring(2, p), property.substring(p + 1));
        }
        else {
          System.setProperty(property.substring(2), "");
        }
      }
    }

    Method main = appData.mainClass.getMethod("main", new Class[]{String[].class});
    main.setAccessible(true);  // need to launch package-private classes
    main.invoke(null, new Object[]{appData.args});
  }

  private static AppData loadMainClassFromClasspathJar(File jarFile, String[] args) throws Exception {
    List properties = Collections.EMPTY_LIST;
    String[] mainArgs;

    JarInputStream inputStream = new JarInputStream(new FileInputStream(jarFile));
    try {
      Manifest manifest = inputStream.getManifest();

      String vmOptions = manifest != null ? manifest.getMainAttributes().getValue("VM-Options") : null;
      if (vmOptions != null) {
        properties = splitBySpaces(vmOptions);
      }

      String programParameters = manifest != null ? manifest.getMainAttributes().getValue("Program-Parameters") : null;
      if (programParameters == null) {
        mainArgs = new String[args.length - 2];
        System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);
      }
      else {
        List list = splitBySpaces(programParameters);
        mainArgs = (String[])list.toArray(new String[0]);
      }
    }
    finally {
      inputStream.close();
      jarFile.deleteOnExit();
    }

    return new AppData(properties, Class.forName(args[1]), mainArgs);
  }

  /**
   * The implementation is copied from copied from com.intellij.util.execution.ParametersListUtil.parse and adapted to old Java versions
   * @noinspection Duplicates
   */
  private static List splitBySpaces(String parameterString) {
    parameterString = parameterString.trim();

    List params = new ArrayList();
    StringBuffer token = new StringBuffer(128);
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

    return params;
  }

  /**
   * args: "classpath file" [ @vm_params "VM options file" ] [ @app_params "args file" ] "main class" [ args ... ]
   */
  private static AppData loadMainClassWithCustomLoader(File classpathFile, String[] args) throws Exception {
    List classpathUrls = new ArrayList();
    StringBuffer classpathString = new StringBuffer();
    List pathElements = readLinesAndDeleteFile(classpathFile);
    for (int i = 0; i < pathElements.size(); i++) {
      String pathElement = (String)pathElements.get(i);
      classpathUrls.add(toUrl(new File(pathElement)));
      if (classpathString.length() > 0) classpathString.append(File.pathSeparator);
      classpathString.append(pathElement);
    }
    System.setProperty("java.class.path", classpathString.toString());

    int startArgsIdx = 2;

    List properties = Collections.EMPTY_LIST;
    if (args.length > startArgsIdx && "@vm_params".equals(args[startArgsIdx - 1])) {
      properties = readLinesAndDeleteFile(new File(args[startArgsIdx]));
      startArgsIdx += 2;
    }

    String[] mainArgs;
    if (args.length > startArgsIdx && "@app_params".equals(args[startArgsIdx - 1])) {
      List lines = readLinesAndDeleteFile(new File(args[startArgsIdx]));
      mainArgs = (String[])lines.toArray(new String[0]);
      startArgsIdx += 2;
    }
    else {
      mainArgs = new String[args.length - startArgsIdx];
      System.arraycopy(args, startArgsIdx, mainArgs, 0, mainArgs.length);
    }

    String mainClassName = args[startArgsIdx - 1];
    ClassLoader loader = new URLClassLoader((URL[])classpathUrls.toArray(new URL[0]), null);
    String systemLoaderName = System.getProperty("java.system.class.loader");
    if (systemLoaderName != null) {
      try {
        loader = (ClassLoader)Class.forName(systemLoaderName).getConstructor(new Class[]{ClassLoader.class}).newInstance(new Object[]{loader});
      }
      catch (Exception ignored) { }
    }
    Class mainClass = loader.loadClass(mainClassName);
    Thread.currentThread().setContextClassLoader(loader);

    return new AppData(properties, mainClass, mainArgs);
  }

  /** @noinspection ResultOfMethodCallIgnored*/
  private static List readLinesAndDeleteFile(File file) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    try {
      List lines = new ArrayList();
      String line;
      while ((line = reader.readLine()) != null) lines.add(line);
      return lines;
    }
    finally {
      reader.close();
      file.delete();
    }
  }

  /** @noinspection Since15, deprecation */
  private static URL toUrl(File classpathElement) throws MalformedURLException {
    URL url;
    try {
      url = classpathElement.toURI().toURL();
    }
    catch (NoSuchMethodError e) {
      url = classpathElement.toURL();
    }
    url = new URL("file", url.getHost(), url.getPort(), url.getFile());
    return url;
  }
}