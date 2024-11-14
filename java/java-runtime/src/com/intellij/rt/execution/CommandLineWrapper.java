// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.execution;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Do not use this command-line method on Java 9+ - use @argfile instead.
 *
 * @author anna
 * @noinspection SSBasedInspection, UseOfSystemOutOrSystemErr
 */
public final class CommandLineWrapper {
  private static final class AppData {
    private final List<String> properties;
    private final Class<?> mainClass;
    private final String[] args;

    private AppData(List<String> properties, Class<?> mainClass, String[] args) {
      this.properties = properties;
      this.mainClass = mainClass;
      this.args = args;
    }
  }

  public static void main(String[] args) throws Exception {
    try {
      Class.forName("java.lang.Module");
      System.err.println(
        "`CommandLineWrapper` is ill-suited for launching apps on Java 9+.\n" +
        "If the run configuration uses \"classpath file\", please change it to \"@argfile\".\n" +
        "Otherwise, please contact support.");
      System.exit(1);
    }
    catch (ClassNotFoundException ignored) { }

    File file = new File(args[0]);
    AppData appData = args[0].endsWith(".jar") ? loadMainClassFromClasspathJar(file, args) : loadMainClassWithCustomLoader(file, args);

    List<String> properties = appData.properties;
    for (String property : properties) {
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

    Method main = appData.mainClass.getMethod("main", String[].class);
    main.setAccessible(true);  // need to launch package-private classes
    main.invoke(null, new Object[]{appData.args});
  }

  private static AppData loadMainClassFromClasspathJar(File jarFile, String[] args) throws Exception {
    List<String> properties = Collections.emptyList();
    String[] mainArgs;

    try (JarInputStream inputStream = new JarInputStream(new FileInputStream(jarFile))) {
      Manifest manifest = inputStream.getManifest();

      String vmOptions = manifest != null ? manifest.getMainAttributes().getValue("VM-Options") : null;
      if (vmOptions != null) {
        properties = splitBySpaces(vmOptions);
      }

      String programParameters = manifest != null ? manifest.getMainAttributes().getValue("Program-Parameters") : null;
      if (programParameters == null) {
        mainArgs = Arrays.copyOfRange(args, 2, args.length);
      }
      else {
        List<String> list = splitBySpaces(programParameters);
        mainArgs = list.toArray(new String[0]);
      }
    }
    finally {
      jarFile.deleteOnExit();
    }

    return new AppData(properties, Class.forName(args[1]), mainArgs);
  }

  /**
   * The implementation is copied from com.intellij.util.execution.ParametersListUtil#parse and adapted to old Java versions.
   */
  private static List<String> splitBySpaces(String parameterString) {
    parameterString = parameterString.trim();

    List<String> params = new ArrayList<>();
    StringBuilder token = new StringBuilder(128);
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
    List<URL> classpathUrls = new ArrayList<>();
    StringBuilder classpathString = new StringBuilder();
    List<String> pathElements = readLinesAndDeleteFile(classpathFile);
    for (String pathElement : pathElements) {
      classpathUrls.add(toUrl(new File(pathElement)));
      if (classpathString.length() > 0) classpathString.append(File.pathSeparator);
      classpathString.append(pathElement);
    }
    System.setProperty("java.class.path", classpathString.toString());

    int startArgsIdx = 2;

    List<String> properties = Collections.emptyList();
    if (args.length > startArgsIdx && "@vm_params".equals(args[startArgsIdx - 1])) {
      properties = readLinesAndDeleteFile(new File(args[startArgsIdx]));
      startArgsIdx += 2;
    }

    String[] mainArgs;
    if (args.length > startArgsIdx && "@app_params".equals(args[startArgsIdx - 1])) {
      List<String> lines = readLinesAndDeleteFile(new File(args[startArgsIdx]));
      mainArgs = lines.toArray(new String[0]);
      startArgsIdx += 2;
    }
    else {
      mainArgs = Arrays.copyOfRange(args, startArgsIdx, args.length);
    }

    String mainClassName = args[startArgsIdx - 1];
    ClassLoader loader = new URLClassLoader(classpathUrls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
    String systemLoaderName = System.getProperty("java.system.class.loader");
    if (systemLoaderName != null) {
      try {
        loader = (ClassLoader)Class.forName(systemLoaderName).getConstructor(new Class[]{ClassLoader.class}).newInstance(new Object[]{loader});
      }
      catch (Exception ignored) { }
    }
    Class<?> mainClass = loader.loadClass(mainClassName);
    Thread.currentThread().setContextClassLoader(loader);

    return new AppData(properties, mainClass, mainArgs);
  }

  /** @noinspection ResultOfMethodCallIgnored */
  private static List<String> readLinesAndDeleteFile(File file) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) lines.add(line);
      return lines;
    }
    finally {
      file.delete();
    }
  }

  /** @noinspection deprecation */
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