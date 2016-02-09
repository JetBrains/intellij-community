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
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VMOptions {
  private static final Logger LOG = Logger.getInstance("#com.intellij.diagnostic.VMOptions");

  public enum MemoryKind {
    HEAP("Xmx", ""), PERM_GEN("XX:MaxPermSize", "="), CODE_CACHE("XX:ReservedCodeCacheSize", "=");

    public final String optionName;
    public final String option;
    public final Pattern pattern;

    MemoryKind(String name, String separator) {
      optionName = name;
      option = "-" + name + separator;
      pattern = Pattern.compile(option + "(\\d*)([a-zA-Z]*)");
    }
  }

  public static int readXmx() {
    return readOption(MemoryKind.HEAP, true);
  }

  public static int readMaxPermGen() {
    return readOption(MemoryKind.PERM_GEN, true);
  }

  public static int readCodeCache() {
    return readOption(MemoryKind.CODE_CACHE, true);
  }

  public static void writeXmx(int value) {
    writeOption(MemoryKind.HEAP, value);
  }

  public static void writeMaxPermGen(int value) {
    writeOption(MemoryKind.PERM_GEN, value);
  }

  public static void writeCodeCache(int value) {
    writeOption(MemoryKind.CODE_CACHE, value);
  }

  public static int readOption(MemoryKind kind, boolean effective) {
    List<String> arguments;
    if (ourTestPath != null) {
      try {
        String content = FileUtil.loadFile(new File(ourTestPath));
        arguments = Collections.singletonList(content);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else if (effective) {
      arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
    }
    else {
      File file = getWriteFile();
      if (file == null || !file.exists()) {
        return -1;
      }

      try {
        String content = FileUtil.loadFile(file);
        arguments = Collections.singletonList(content);
      }
      catch (IOException e) {
        LOG.warn(e);
        return -1;
      }
    }

    for (String argument : arguments) {
      Matcher m = kind.pattern.matcher(argument);
      if (m.find()) {
        try {
          int value = Integer.parseInt(m.group(1));
          double multiplier = parseUnit(m.group(2));
          return (int)(value * multiplier);
        }
        catch (NumberFormatException e) {
          LOG.info(e);
          break;
        }
      }
    }

    return -1;
  }

  private static double parseUnit(String unitString) {
    if (StringUtil.startsWithIgnoreCase(unitString, "k")) return (double)1 / 1024;
    if (StringUtil.startsWithIgnoreCase(unitString, "g")) return 1024;
    return 1;
  }

  private static void writeOption(MemoryKind option, int value) {
    File file = getWriteFile();
    if (file == null) return;

    try {
      String content = file.exists() ? FileUtil.loadFile(file) : read();

      String optionValue = option.option + value + "m";

      if (!StringUtil.isEmptyOrSpaces(content)) {
        Matcher m = option.pattern.matcher(content);
        if (m.find()) {
          StringBuffer b = new StringBuffer();
          m.appendReplacement(b, Matcher.quoteReplacement(optionValue));
          m.appendTail(b);
          content = b.toString();
        }
        else {
          content = StringUtil.trimTrailing(content) + SystemProperties.getLineSeparator() + optionValue;
        }
      }
      else {
        content = optionValue;
      }

      if (file.exists()) {
        FileUtil.setReadOnlyAttribute(file.getPath(), false);
      }
      else {
        FileUtil.ensureExists(file.getParentFile());
      }

      FileUtil.writeToFile(file, content);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Nullable
  public static String read() {
    try {
      File newFile = getWriteFile();
      if (newFile != null && newFile.exists()) {
        return FileUtil.loadFile(newFile);
      }

      String vmOptionsFile = System.getProperty("jb.vmOptionsFile");
      if (vmOptionsFile != null) {
        return FileUtil.loadFile(new File(vmOptionsFile));
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }

    return null;
  }

  @Nullable
  public static File getWriteFile() {
    if (ourTestPath != null) {
      return new File(ourTestPath);
    }

    String vmOptionsFile = System.getProperty("jb.vmOptionsFile");
    if (vmOptionsFile == null) {
      // launchers should specify a path to an options file used to configure a JVM
      LOG.warn("VM options file path missing");
      return null;
    }

    if (!FileUtil.isAncestor(PathManager.getHomePath(), vmOptionsFile, true)) {
      // a file is located outside the IDE installation - meaning it is safe to overwrite
      return new File(vmOptionsFile);
    }

    String location = PathManager.getCustomOptionsDirectory();
    if (location == null) {
      LOG.warn("custom options directory not specified (" + PathManager.PROPERTY_PATHS_SELECTOR + " not set?)");
      return null;
    }

    return new File(location, getCustomFileName());
  }

  @NotNull
  public static String getCustomFileName() {
    StringBuilder sb = new StringBuilder(32);
    sb.append(ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US));
    if (SystemInfo.is64Bit && !SystemInfo.isMac) sb.append("64");
    if (SystemInfo.isWindows) sb.append(".exe");
    sb.append(".vmoptions");
    return sb.toString();
  }

  private static String ourTestPath;

  @TestOnly
  static void setTestFile(String path) {
    ourTestPath = path;
  }

  @TestOnly
  static void clearTestFile() {
    ourTestPath = null;
  }
}