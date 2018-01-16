/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    HEAP("Xmx", ""), PERM_GEN("XX:MaxPermSize", "="), METASPACE("XX:MaxMetaspaceSize", "="), CODE_CACHE("XX:ReservedCodeCacheSize", "=");

    public final String optionName;
    public final String option;
    private final Pattern pattern;

    MemoryKind(String name, String separator) {
      optionName = name;
      option = "-" + name + separator;
      pattern = Pattern.compile(option + "(\\d*)([a-zA-Z]*)");
    }
  }

  public static int readOption(@NotNull MemoryKind kind, boolean effective) {
    List<String> arguments;
    if (effective) {
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

  public static void writeOption(@NotNull MemoryKind option, int value) {
    String optionValue = option.option + value + "m";
    writeGeneralOption(option.pattern, optionValue);
  }

  public static void writeOption(@NotNull String option, @NotNull String separator, @NotNull String value) {
    writeGeneralOption(Pattern.compile("-D" + option + separator + "(true|false)*([a-zA-Z]*)"), "-D" + option + separator + value);
  }


  private static void writeGeneralOption(@NotNull Pattern pattern, @NotNull String value) {
    File file = getWriteFile();
    if (file == null) {
      LOG.warn("VM options file not configured");
      return;
    }

    try {
      String content = file.exists() ? FileUtil.loadFile(file) : read();

      if (!StringUtil.isEmptyOrSpaces(content)) {
        Matcher m = pattern.matcher(content);
        if (m.find()) {
          StringBuffer b = new StringBuffer();
          m.appendReplacement(b, Matcher.quoteReplacement(value));
          m.appendTail(b);
          content = b.toString();
        }
        else {
          content = StringUtil.trimTrailing(content) + SystemProperties.getLineSeparator() + value;
        }
      }
      else {
        content = value;
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
    String vmOptionsFile = System.getProperty("jb.vmOptionsFile");
    if (vmOptionsFile == null) {
      // launchers should specify a path to an options file used to configure a JVM
      return null;
    }

    vmOptionsFile = new File(vmOptionsFile).getAbsolutePath();
    if (!FileUtil.isAncestor(canonicalPath(PathManager.getHomePath()), canonicalPath(vmOptionsFile), true)) {
      // a file is located outside the IDE installation - meaning it is safe to overwrite
      return new File(vmOptionsFile);
    }

    String location = PathManager.getCustomOptionsDirectory();
    if (location == null) {
      return null;
    }

    String fileName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
    if (SystemInfo.is64Bit && !SystemInfo.isMac) fileName += "64";
    if (SystemInfo.isWindows) fileName += ".exe";
    fileName += ".vmoptions";
    return new File(location, fileName);
  }

  private static String canonicalPath(String path) {
    try {
      return new File(path).getCanonicalPath();
    }
    catch (IOException e) {
      return path;
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #readOption(MemoryKind, boolean)} (to be removed in IDEA 2018) */
  public static int readXmx() {
    return readOption(MemoryKind.HEAP, true);
  }

  /** @deprecated use {@link #readOption(MemoryKind, boolean)} (to be removed in IDEA 2018) */
  public static int readMaxPermGen() {
    return readOption(MemoryKind.PERM_GEN, true);
  }

  /** @deprecated use {@link #readOption(MemoryKind, boolean)} (to be removed in IDEA 2018) */
  public static int readCodeCache() {
    return readOption(MemoryKind.CODE_CACHE, true);
  }

  /** @deprecated use {@link #writeOption(MemoryKind, int)} (to be removed in IDEA 2018) */
  public static void writeXmx(int value) {
    writeOption(MemoryKind.HEAP, value);
  }

  /** @deprecated use {@link #writeOption(MemoryKind, int)} (to be removed in IDEA 2018) */
  public static void writeMaxPermGen(int value) {
    writeOption(MemoryKind.PERM_GEN, value);
  }

  /** @deprecated use {@link #writeOption(MemoryKind, int)} (to be removed in IDEA 2018) */
  public static void writeCodeCache(int value) {
    writeOption(MemoryKind.CODE_CACHE, value);
  }
  //</editor-fold>
}