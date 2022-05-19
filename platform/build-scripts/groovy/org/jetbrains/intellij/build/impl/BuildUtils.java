// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.xml.dom.XmlDomReader;
import com.intellij.util.xml.dom.XmlElement;
import groovy.lang.Closure;
import org.apache.tools.ant.Main;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BuildUtils {
  public static String replaceAll(String text, Map<String, String> replacements, String marker) {
    DefaultGroovyMethods.each(replacements, new Closure<String>(null, null) {
      public String doCall(Map.Entry<String, String> it) {
        return text = text.replace(marker + it.getKey() + marker, it.getValue());
      }

      public String doCall() {
        return doCall(null);
      }
    });
    return text;
  }

  public static String replaceAll(String text, Map<String, String> replacements) {
    return BuildUtils.replaceAll(text, replacements, "__");
  }

  public static void replaceAll(Path file, String marker, String... replacements) {
    String text = Files.readString(file);
    for (int i = 0; ; i < replacements.length ;){
      text = text.replace(marker + replacements[i] + marker, replacements[i + 1]);
    }

    Files.writeString(file, text);
  }

  public static String replaceAll(String text, String marker, String... replacements) {
    for (int i = 0; ; i < replacements.length ;){
      text = text.replace(marker + replacements[i] + marker, replacements[i + 1]);
    }

    return text;
  }

  public static void copyAndPatchFile(@NotNull Path sourcePath,
                                      @NotNull Path targetPath,
                                      Map<String, String> replacements,
                                      String marker,
                                      String lineSeparator) {
    Files.createDirectories(targetPath.getParent());
    String content = replaceAll(Files.readString(sourcePath), replacements, marker);
    if (!lineSeparator.isEmpty()) {
      content = StringUtilRt.convertLineSeparators(content, lineSeparator);
    }

    Files.writeString(targetPath, content);
  }

  public static void copyAndPatchFile(@NotNull Path sourcePath, @NotNull Path targetPath, Map<String, String> replacements, String marker) {
    BuildUtils.copyAndPatchFile(sourcePath, targetPath, replacements, marker, "");
  }

  public static void copyAndPatchFile(@NotNull Path sourcePath, @NotNull Path targetPath, Map<String, String> replacements) {
    BuildUtils.copyAndPatchFile(sourcePath, targetPath, replacements, "__", "");
  }

  public static void assertUnixLineEndings(@NotNull Path file) {
    if (Files.readString(file).contains("\r")) {
      throw new IllegalStateException("Text file must not contain \r (CR or CRLF) line endings: " + String.valueOf(file));
    }
  }

  public static PrintStream getRealSystemOut() {
    try {
      //if the build script is running under Ant or AntBuilder it may replace the standard System.out
      Field field = Main.class.getDeclaredField("out");
      field.setAccessible(true);
      return (PrintStream)field.get(null);// No longer works in recent Ant 1.9.x and 1.10
    }
    catch (Throwable ignored) {
    }

    try {
      Class<?> clazz = (Class<?>)Class.class.forName("org.jetbrains.jps.gant.GantWithClasspathTask");
      Field field = clazz.getDeclaredField("out");
      field.setAccessible(true);
      Object out = field.get(null);
      if (out != null) return DefaultGroovyMethods.asType(out, PrintStream.class);
    }
    catch (Throwable ignored) {
    }

    return System.out;
  }

  public static List<String> propertiesToJvmArgs(Map<String, Object> properties) {
    List<String> result = new ArrayList<String>(properties.size());
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      addVmProperty(result, entry.getKey(), entry.getValue().toString());
    }

    return result;
  }

  public static void addVmProperty(@NotNull List<String> args, @NotNull String key, @Nullable String value) {
    if (value != null) {
      args.add("-D" + key + "=" + value);
    }
  }

  public static void convertLineSeparators(@NotNull Path file, @NotNull String newLineSeparator) {
    String data = Files.readString(file);
    String convertedData = StringUtilRt.convertLineSeparators(data, newLineSeparator);
    if (!data.equals(convertedData)) {
      Files.writeString(file, convertedData);
    }
  }

  public static List<Path> getPluginJars(Path pluginPath) {
    return IOGroovyMethods.withCloseable(Files.newDirectoryStream(pluginPath.resolve("lib"), "*.jar"), new Closure<List<Path>>(null, null) {
      public List<Path> doCall(DirectoryStream<Path> it) { return DefaultGroovyMethods.toList(it); }

      public List<Path> doCall() {
        return doCall(null);
      }
    });
  }

  @Nullable
  public static String readPluginId(Path pluginJar) {
    if (!pluginJar.toString().endsWith(".jar") || !Files.isRegularFile(pluginJar)) {
      return null;
    }


    try {
      return IOGroovyMethods.withCloseable(FileSystems.newFileSystem(pluginJar, (ClassLoader)null), new Closure<String>(null, null) {
        public String doCall(FileSystem it) {
          final XmlElement child = XmlDomReader.readXmlAsModel(Files.newInputStream(it.getPath("META-INF/plugin.xml"))).getChild("id");
          return (child == null ? null : child.content);
        }

        public String doCall() {
          return doCall(null);
        }
      });
    }
    catch (NoSuchFileException ignore) {
      return null;
    }
  }

  public static boolean isUnderJpsBootstrap() {
    return System.getenv("JPS_BOOTSTRAP_COMMUNITY_HOME") != null;
  }
}
