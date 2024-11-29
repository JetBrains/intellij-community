// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class VMOptions {
  private static final Logger LOG = Logger.getInstance(VMOptions.class);
  private static final ReadWriteLock ourUserFileLock = new ReentrantReadWriteLock();

  private VMOptions() { }

  public enum MemoryKind {
    HEAP("Xmx", "", "change.memory.max.heap"),
    MIN_HEAP("Xms", "", "change.memory.min.heap"),
    METASPACE("XX:MaxMetaspaceSize", "=", "change.memory.metaspace"),
    DIRECT_BUFFERS("XX:MaxDirectMemorySize", "=", "change.memory.direct.buffers"),
    CODE_CACHE("XX:ReservedCodeCacheSize", "=", "change.memory.code.cache");

    public final @NlsSafe String optionName;
    public final String option;
    private final String labelKey;

    MemoryKind(String name, String separator, @PropertyKey(resourceBundle = "messages.IdeBundle") String key) {
      optionName = name;
      option = '-' + name + separator;
      labelKey = key;
    }

    public @NlsContexts.Label String label() {
      return IdeBundle.message(labelKey);
    }
  }

  /**
   * Returns a value of the given {@link MemoryKind memory setting} (in MiBs), or {@code -1} when unable to find out
   * (e.g., a user doesn't have custom memory settings).
   *
   * @see #readOption(String, boolean)
   */
  public static int readOption(@NotNull MemoryKind kind, boolean effective) {
    var strValue = readOption(kind.option, effective);
    if (strValue != null) {
      try {
        return (int)(parseMemoryOption(strValue) >> 20);
      }
      catch (IllegalArgumentException e) {
        LOG.info(e);
      }
    }
    return -1;
  }

  /**
   * Returns a value of the given option, or {@code null} when unable to find.
   *
   * @param effective when {@code true}, the method returns a value for the current JVM (from {@link ManagementFactory#getRuntimeMXBean()}),
   *                  otherwise it reads a user's .vmoptions {@link #getUserOptionsFile() file}.
   */
  public static @Nullable String readOption(@NotNull String prefix, boolean effective) {
    var lines = options(effective);
    // the list is iterated in the reverse order, because the last value wins
    for (int i = lines.size() - 1; i >= 0; i--) {
      var line = lines.get(i).trim();
      if (line.startsWith(prefix)) {
        return line.substring(prefix.length());
      }
    }
    return null;
  }

  /**
   * Returns a (possibly empty) list of the given option's values.
   *
   * @see #readOption(String, boolean)
   */
  public static @NotNull List<String> readOptions(@NotNull String prefix, boolean effective) {
    var values = new SmartList<String>();
    for (var s : options(effective)) {
      var line = s.trim();
      if (line.startsWith(prefix)) {
        values.add(line.substring(prefix.length()));
      }
    }
    return values;
  }

  private static List<String> options(boolean effective) {
    if (effective) {
      return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }
    else {
      List<String> platformOptions = List.of(), userOptions = List.of();

      var platformFile = getPlatformOptionsFile();
      if (Files.exists(platformFile)) {
        try {
          platformOptions = Files.readAllLines(platformFile, getFileCharset());
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }

      var userFile = getUserOptionsFile();
      if (userFile != null && Files.exists(userFile)) {
        ourUserFileLock.readLock().lock();
        try {
          userOptions = Files.readAllLines(userFile, getFileCharset());
        }
        catch (IOException e) {
          LOG.warn(e);
        }
        finally {
          ourUserFileLock.readLock().unlock();
        }
      }

      var result = new ArrayList<String>(platformOptions.size() + userOptions.size());
      result.addAll(platformOptions);
      result.addAll(userOptions);
      return result;
    }
  }

  /**
   * Parses VM memory option (such as "-Xmx") string value and returns its numeric value (in bytes).
   * See <a href="https://docs.oracle.com/en/java/javase/16/docs/specs/man/java.html#extra-options-for-java">'java' command manual</a>
   * for the syntax.
   *
   * @throws IllegalArgumentException when either a number or a unit is invalid
   */
  public static long parseMemoryOption(@NotNull String strValue) throws IllegalArgumentException {
    int p = 0;
    while (p < strValue.length() && Strings.isDecimalDigit(strValue.charAt(p))) p++;
    long numValue = Long.parseLong(strValue.substring(0, p));
    if (p < strValue.length()) {
      String unit = strValue.substring(p);
      if ("k".equalsIgnoreCase(unit)) numValue <<= 10;
      else if ("m".equalsIgnoreCase(unit)) numValue <<= 20;
      else if ("g".equalsIgnoreCase(unit)) numValue <<= 30;
      else throw new IllegalArgumentException("Invalid unit: " + unit);
    }
    return numValue;
  }

  /**
   * Sets or deletes a Java memory limit (in MiBs). See {@link #setOption(String, String)} for details.
   */
  public static void setOption(@NotNull MemoryKind option, int value) throws IOException {
    setOption(option.option, value > 0 ? value + "m" : null);
  }

  /**
   * Sets or deletes a Java system property. See {@link #setOption(String, String)} for details.
   */
  public static void setProperty(@NotNull String name, @Nullable String newValue) throws IOException {
    setOption("-D" + name + '=', newValue);
  }

  /**
   * <p>Sets or deletes a VM option in a user's .vmoptions {@link #getUserOptionsFile() file}.</p>
   *
   * <p>When {@code newValue} is {@code null}, all options that start with a given prefix are removed from the file.
   * When {@code newValue} is not {@code null} and an option is present in the file, it's value is replaced, otherwise
   * the option is added to the file.</p>
   */
  public static void setOption(@NotNull String prefix, @Nullable String newValue) throws IOException {
    setOptions(List.of(new Pair<>(prefix, newValue)));
  }

  /**
   * Sets or deletes multiple options in one pass. See {@link #setOption(String, String)} for details.
   */
  public static void setOptions(@NotNull List<? extends Pair<@NotNull String, @Nullable String>> _options) throws IOException {
    var file = getUserOptionsFile();
    if (file == null) {
      throw new IOException("The IDE is not configured for using custom VM options (jb.vmOptionsFile=" + System.getProperty("jb.vmOptionsFile") + ')');
    }

    var lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file, getFileCharset())) : new ArrayList<String>();
    var options = new ArrayList<Pair<String, @Nullable String>>(_options);
    var modified = false;

    for (var il = lines.listIterator(lines.size()); il.hasPrevious(); ) {
      var line = il.previous().trim();
      for (var io = options.iterator(); io.hasNext(); ) {
        var option = io.next();
        if (line.startsWith(option.first)) {
          if (option.second == null) {
            il.remove();
            modified = true;
          }
          else {
            var newLine = option.first + option.second;
            if (!newLine.equals(line)) {
              il.set(newLine);
              modified = true;
            }
            io.remove();
          }
          break;
        }
      }
    }

    for (var option : options) {
      if (option.second != null) {
        lines.add(option.first + option.second);
        modified = true;
      }
    }

    if (modified) {
      NioFiles.createDirectories(file.getParent());
      ourUserFileLock.writeLock().lock();
      try {
        Files.write(file, lines, getFileCharset());
      }
      finally {
        ourUserFileLock.writeLock().unlock();
      }
    }
  }

  /**
   * Returns {@code true} when user's VM options may be created (or already exists) -
   * i.e., when the IDE knows a place where a launcher will look for that file.
   */
  public static boolean canWriteOptions() {
    return getUserOptionsFile() != null;
  }

  @ApiStatus.Internal
  public static @NotNull Path getPlatformOptionsFile() {
    return Path.of(PathManager.getBinPath(), getFileName());
  }

  @ApiStatus.Internal
  public static @Nullable Path getUserOptionsFile() {
    var vmOptionsFile = System.getProperty("jb.vmOptionsFile");
    if (vmOptionsFile == null) {
      // launchers should specify a path to a VM options file used to configure a JVM
      return null;
    }

    var candidate = Path.of(vmOptionsFile).toAbsolutePath();
    if (!PathManager.isUnderHomeDirectory(candidate)) {
      // a file is located outside the IDE installation - meaning it is safe to overwrite
      return candidate;
    }

    var location = PathManager.getCustomOptionsDirectory();
    if (location == null) {
      return null;
    }

    return Path.of(location, getFileName());
  }

  @ApiStatus.Internal
  public static @NotNull String getFileName() {
    var fileName = ApplicationNamesInfo.getInstance().getScriptName();
    if (!SystemInfo.isMac) fileName += "64";
    if (SystemInfo.isWindows) fileName += ".exe";
    fileName += ".vmoptions";
    return fileName;
  }

  @ApiStatus.Internal
  public static @NotNull Charset getFileCharset() {
    return CharsetToolkit.getPlatformCharset();
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated ignores write errors; please use {@link #setProperty} instead */
  @Deprecated(forRemoval = true)
  public static void writeOption(@NotNull String option, @NotNull String separator, @NotNull String value) {
    try {
      setOption("-D" + option + separator, value);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  /**
   * @deprecated since 2021.3, the result may be incomplete: launchers collect VM options from two files, but this method returns
   * only one of them (see IDEA-240526 for more details). In addition, clients have to deal with platform-specific line separators and charsets,
   * and manipulating the whole content of the file cannot guarantee thread-safety.
   * Please use {@link #readOption}/{@link #setOption} methods instead.
   */
  @Deprecated(forRemoval = true)
  public static @Nullable String read() {
    try {
      Path newFile = getUserOptionsFile();
      if (newFile != null && Files.exists(newFile)) {
        return Files.readString(newFile, getFileCharset());
      }

      String vmOptionsFile = System.getProperty("jb.vmOptionsFile");
      if (vmOptionsFile != null) {
        return Files.readString(Path.of(vmOptionsFile), getFileCharset());
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }

    return null;
  }

  /** @deprecated please see {@link #read()} for details */
  @Deprecated(forRemoval = true)
  public static @Nullable Path getWriteFile() {
    return getUserOptionsFile();
  }
  //</editor-fold>
}
