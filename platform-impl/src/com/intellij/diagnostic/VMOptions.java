package com.intellij.diagnostic;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VMOptions {
  private static final String MEM_SIZE_EXPR = "(\\d*)([a-zA-Z]*)";
  private static final String XMX_OPTION = "-Xmx";
  private static final String PERM_GEN_OPTION = "-XX:MaxPermSize=";

  private static final Pattern XMX_PATTERN = Pattern.compile(XMX_OPTION + MEM_SIZE_EXPR);
  private static final Pattern PERM_GEN_PATTERN = Pattern.compile(PERM_GEN_OPTION + MEM_SIZE_EXPR);
  private static final Pattern MAC_OS_VM_OPTIONS_PATTERN = Pattern.compile("(<key>VMOptions</key>\\s*<string>)(.*)(</string>)");

  private static String ourTestPath;
  private static boolean ourTestMacOs;

  public static void setTestFile(String path, boolean isMacOs) {
    ourTestPath = path;
    ourTestMacOs = isMacOs;
  }

  public static void clearTestFile() {
    ourTestPath = null;
  }

  public static int readXmx() throws IOException {
    return readOption(XMX_PATTERN);
  }

  public static void writeXmx(int value) throws IOException {
    writeOption(XMX_OPTION, value, XMX_PATTERN);
  }

  public static int readMaxPermGen() throws IOException {
    return readOption(PERM_GEN_PATTERN);
  }

  public static void writeMaxPermGen(int value) throws IOException {
    writeOption(PERM_GEN_OPTION, value, PERM_GEN_PATTERN);
  }

  private static int readOption(Pattern pattern) throws IOException {
    String content = new String(FileUtil.loadFileText(getFile()));

    if (isMacOs()) {
      content = extractMacOsVMOptionsSection(content);
      if (content == null) return -1;
    }

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
      return -1;
    }
  }

  private static double parseUnit(String unitString) {
    if (StringUtil.startsWithIgnoreCase(unitString, "k")) return (double)1 / 1024;
    if (StringUtil.startsWithIgnoreCase(unitString, "g")) return 1024;
    return 1;
  }

  private static void writeOption(String option, int value, Pattern pattern) throws IOException {
    String optionValue = option + value + "m";
    String content = new String(FileUtil.loadFileText(getFile()));
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

    FileUtil.writeToFile(getFile(), content.getBytes());
  }

  public static String extractMacOsVMOptionsSection(String text) {
    Matcher m = MAC_OS_VM_OPTIONS_PATTERN.matcher(text);
    if (!m.find()) return null;
    return m.group();
  }

  public static String extractMacOsVMOptions(String text) {
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

  private static File getFile() {
    return new File(ourTestPath);
  }

  private static boolean isMacOs() {
    if (ourTestPath != null) return ourTestMacOs;
    return false;
  }
}
