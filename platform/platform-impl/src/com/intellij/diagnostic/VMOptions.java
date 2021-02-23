// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VMOptions {
  private static final Logger LOG = Logger.getInstance(VMOptions.class);

  public enum MemoryKind {
    HEAP("Xmx", "", "change.memory.max.heap"),
    MIN_HEAP("Xms", "", "change.memory.min.heap"),
    METASPACE("XX:MaxMetaspaceSize", "=", "change.memory.metaspace"),
    CODE_CACHE("XX:ReservedCodeCacheSize", "=", "change.memory.code.cache");

    public final @NlsSafe String optionName;
    public final String option;
    private final Pattern pattern;
    private final String labelKey;

    MemoryKind(String name, String separator, @PropertyKey(resourceBundle = "messages.DiagnosticBundle") String key) {
      optionName = name;
      option = "-" + name + separator;
      pattern = Pattern.compile(option + "(\\d*)([a-zA-Z]*)");
      labelKey = key;
    }

    public @NlsContexts.Label String label() {
      return DiagnosticBundle.message(labelKey);
    }
  }

  public static int readOption(@NotNull MemoryKind kind, boolean effective) {
    List<String> arguments;
    if (effective) {
      arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
    }
    else {
      Path file = getWriteFile();
      if (file == null || !Files.exists(file)) {
        return -1;
      }

      try {
        String content = FileUtil.loadFile(file.toFile());
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
    writeGeneralOption(Pattern.compile("-D" + option + separator + "(true|false)*([a-zA-Z0-9]*)"), "-D" + option + separator + value);
  }

  public static void writeEnableCDSArchiveOption(@NotNull final String archivePath) {
    writeGeneralOptions(
      Function.<String>identity()
        .andThen(replaceOrAddOption(Pattern.compile("-Xshare:.*"), "-Xshare:auto"))
        .andThen(replaceOrAddOption(Pattern.compile("-XX:\\+UnlockDiagnosticVMOptions"), "-XX:+UnlockDiagnosticVMOptions"))
        .andThen(replaceOrAddOption(Pattern.compile("-XX:SharedArchiveFile=.*"), "-XX:SharedArchiveFile=" + archivePath))
    );
  }

  public static void writeDisableCDSArchiveOption() {
    writeGeneralOptions(
      Function.<String>identity()
        .andThen(replaceOrAddOption(Pattern.compile("-Xshare:.*\\r?\\n?"), ""))
        // we cannot remove "-XX:+UnlockDiagnosticVMOptions", we do not know the reason it was included
        .andThen(replaceOrAddOption(Pattern.compile("-XX:SharedArchiveFile=.*\\r?\\n?"), ""))
    );
  }

  private static void writeGeneralOption(@NotNull Pattern pattern, @NotNull String value) {
    writeGeneralOptions(replaceOrAddOption(pattern, value));
  }

  @NotNull
  private static Function<String, String> replaceOrAddOption(@NotNull Pattern pattern, @NotNull String value) {
    return content -> {
      if (!StringUtil.isEmptyOrSpaces(content)) {
        Matcher m = pattern.matcher(content);
        if (m.find()) {
          StringBuilder b = new StringBuilder();
          m.appendReplacement(b, Matcher.quoteReplacement(value));
          m.appendTail(b);
          content = b.toString();
        }
        else if (!StringUtil.isEmptyOrSpaces(value)) {
          content = StringUtil.trimTrailing(content) + System.lineSeparator() + value;
        }
      }
      else {
        content = value;
      }

      return content;
    };
  }

  private static void writeGeneralOptions(@NotNull Function<? super String, String> transformContent) {
    Path path = getWriteFile();
    if (path == null) {
      LOG.warn("VM options file not configured");
      return;
    }

    File file = path.toFile();
    try {
      String content = file.exists() ? FileUtil.loadFile(file) : read();
      content = transformContent.apply(content);

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

  public static boolean canWriteOptions() {
    return getWriteFile() != null;
  }

  @Nullable
  public static String read() {
    try {
      Path newFile = getWriteFile();
      if (newFile != null && Files.exists(newFile)) {
        return FileUtil.loadFile(newFile.toFile());
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

  public static @Nullable Path getWriteFile() {
    String vmOptionsFile = System.getProperty("jb.vmOptionsFile");
    if (vmOptionsFile == null) {
      // launchers should specify a path to a VM options file used to configure a JVM
      return null;
    }

    vmOptionsFile = new File(vmOptionsFile).getAbsolutePath();
    if (!PathManager.isUnderHomeDirectory(vmOptionsFile)) {
      // a file is located outside the IDE installation - meaning it is safe to overwrite
      return Paths.get(vmOptionsFile);
    }

    String location = PathManager.getCustomOptionsDirectory();
    if (location == null) {
      return null;
    }

    return Paths.get(location, getCustomVMOptionsFileName());
  }

  @NotNull
  public static String getCustomVMOptionsFileName() {
    String fileName = ApplicationNamesInfo.getInstance().getScriptName();
    if (!SystemInfo.isMac && CpuArch.isIntel64()) fileName += "64";
    if (SystemInfo.isWindows) fileName += ".exe";
    fileName += ".vmoptions";
    return fileName;
  }
}
