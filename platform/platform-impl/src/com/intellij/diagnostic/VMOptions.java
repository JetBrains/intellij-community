/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VMOptions {
  private static final Logger LOG = Logger.getInstance("#com.intellij.diagnostic.VMOptions");

  @NonNls static final String XMX_OPTION_NAME = "Xmx";
  @NonNls static final String PERM_GEN_OPTION_NAME = "XX:MaxPermSize";
  @NonNls static final String CODE_CACHE_OPTION_NAME = "XX:ReservedCodeCacheSize";
  @NonNls static final String MAC_ARCH_VM_OPTIONS = SystemInfo.is64Bit ? "VMOptions.x86_64" : "VMOptions.i386";

  @NonNls private static final String XMX_OPTION = "-" + XMX_OPTION_NAME;
  @NonNls private static final String PERM_GEN_OPTION = "-" + PERM_GEN_OPTION_NAME + "=";
  @NonNls private static final String CODE_CACHE_OPTION = "-" + CODE_CACHE_OPTION_NAME + "=";

  @NonNls private static final String MEM_SIZE_EXPR = "(\\d*)([a-zA-Z]*)";
  @NonNls private static final Pattern XMX_PATTERN = Pattern.compile(XMX_OPTION + MEM_SIZE_EXPR);
  @NonNls private static final Pattern PERM_GEN_PATTERN = Pattern.compile(PERM_GEN_OPTION + MEM_SIZE_EXPR);
  @NonNls private static final Pattern CODE_CACHE_PATTERN = Pattern.compile(CODE_CACHE_OPTION + MEM_SIZE_EXPR);
  @NonNls private static final Pattern MAC_OS_VM_OPTIONS_PATTERN =
    Pattern.compile("(<key>" + MAC_ARCH_VM_OPTIONS + "</key>(?:(?:\\s*)(?:<!--(?:.*)-->(?:\\s*))*)<string>)(.*)(</string>)");

  @NonNls private static final String INFO_PLIST = "/Contents/Info.plist";

  private static String ourTestPath;
  private static boolean ourTestMacOs;

  @TestOnly
  static void setTestFile(String path, boolean isMacOs) {
    ourTestPath = path;
    ourTestMacOs = isMacOs;
  }

  @TestOnly
  static void clearTestFile() {
    ourTestPath = null;
  }

  public static int readXmx() {
    return readOption(XMX_PATTERN);
  }

  public static void writeXmx(int value) {
    writeOption(XMX_OPTION, value, XMX_PATTERN);
  }

  public static int readMaxPermGen() {
    return readOption(PERM_GEN_PATTERN);
  }

  public static int readCodeCache() {
    return readOption(CODE_CACHE_PATTERN);
  }

  public static void writeMaxPermGen(int value) {
    writeOption(PERM_GEN_OPTION, value, PERM_GEN_PATTERN);
  }

  public static void writeCodeCache(int value) {
    writeOption(CODE_CACHE_OPTION, value, CODE_CACHE_PATTERN);
  }

  @Nullable
  public static String read() {
    File file = getFile();
    if (file == null) return null;

    try {
      String content = FileUtil.loadFile(file);
      if (isMacOs()) {
        content = extractMacOsVMOptions(content);
      }
      return content;
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  private static int readOption(Pattern pattern) {
    String content = read();
    if (content == null) return -1;

    Matcher m = pattern.matcher(content);
    if (!m.find()) return -1;

    String valueString = m.group(1);
    String unitString = m.group(2);

    try {
      int value = Integer.parseInt(valueString);
      double multiplier = parseUnit(unitString);

      return (int)(value * multiplier);
    }
    catch (NumberFormatException e) {
      LOG.info(e);
      return -1;
    }
  }

  private static double parseUnit(String unitString) {
    if (StringUtil.startsWithIgnoreCase(unitString, "k")) return (double)1 / 1024;
    if (StringUtil.startsWithIgnoreCase(unitString, "g")) return 1024;
    return 1;
  }

  private static void writeOption(String option, int value, Pattern pattern) {
    File file = getFile();
    if (file == null) return;

    try {
      String optionValue = option + value + "m";
      String content = FileUtil.loadFile(file);
      String vmOptions;

      if (isMacOs()) {
        vmOptions = extractMacOsVMOptions(content);
        if (vmOptions == null) return;
      }
      else {
        vmOptions = content;
      }

      vmOptions = replace(pattern, vmOptions, optionValue, "", "", vmOptions + " " + optionValue);

      if (isMacOs()) {
        content = replace(MAC_OS_VM_OPTIONS_PATTERN, content, vmOptions, "$1", "$3", content);
      }
      else {
        content = vmOptions;
      }

      FileUtil.setReadOnlyAttribute(file.getPath(), false);
      FileUtil.writeToFile(file, content.getBytes());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @Nullable
  private static String extractMacOsVMOptions(String text) {
    Matcher m = MAC_OS_VM_OPTIONS_PATTERN.matcher(text);
    if (!m.find()) return null;
    return m.group(2);
  }

  private static String replace(Pattern pattern,
                                String text,
                                String replacement,
                                String prefix,
                                String suffix,
                                String defaultResult) {
    Matcher m = pattern.matcher(text);
    if (!m.find()) return defaultResult;

    StringBuffer b = new StringBuffer();
    m.appendReplacement(b, prefix + Matcher.quoteReplacement(replacement) + suffix);
    m.appendTail(b);

    return b.toString();
  }

  @Nullable
  private static File getFile() {
    final String path = ourTestPath != null ? ourTestPath : getSettingsFilePath();
    return path != null ? new File(path) : null;
  }

  @NonNls
  @Nullable
  public static String getSettingsFilePath() {
    final File f = new File(doGetSettingsFilePath()).getAbsoluteFile();
    if (!f.exists()) return null;

    try {
      return f.getCanonicalPath();
    }
    catch (IOException e) {
      LOG.debug(e);
      return f.getPath();
    }
  }

  @NotNull
  private static String doGetSettingsFilePath() {
    final String vmOptionsFile = System.getProperty("jb.vmOptionsFile");
    if (!StringUtil.isEmptyOrSpaces(vmOptionsFile)) {
      return vmOptionsFile;
    }

    if (SystemInfo.isMac) {
      return PathManager.getHomePath() + INFO_PLIST;
    }

    final String productName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase();
    final String platformSuffix = SystemInfo.is64Bit ? "64" : "";
    final String osSuffix = SystemInfo.isWindows ? ".exe" : "";
    return PathManager.getBinPath() + File.separatorChar + productName + platformSuffix + osSuffix + ".vmoptions";
  }

  private static boolean isMacOs() {
    return ourTestPath != null ? ourTestMacOs : SystemInfo.isMac;
  }
}
