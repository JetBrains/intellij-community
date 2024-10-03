// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.util.PathUtil;
import com.intellij.util.Producer;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Provides 3 streams of output named system, stdout and stderr,
 * which are independent of Java's {@link System#out} and {@link System#err}
 * but analogous to them.
 * <p>
 * This output can be validated against prerecorded output in <tt>{@link #myAppPath}/outs</tt>.
 * The output files are named <i>testName[.platform][.jdkX].out</i>,
 * where <i>platform</i> is either {@code unx} or {@code win}
 * and <i>jdkX</i> tries all Java versions from the current SDK version down to 7.
 * The output files contain the messages from the system, stdout and stderr streams,
 * in this order, without any separators.
 * <p>
 * Call {@link #init(String)} to provide the test name,
 * then write output using {@link #print(String, Key)} and {@link #println(String, Key)}
 * and finally validate using {@link #checkValid(Sdk)}.
 * <p>
 * The output is filtered and normalized to make it platform-independent.
 * To add custom normalization, override {@link #replaceAdditionalInOutput(String)}.
 */
public class OutputChecker {
  public static final ProcessOutputType[] OUTPUT_ORDER = {
    ProcessOutputType.SYSTEM,
    ProcessOutputType.STDOUT,
    ProcessOutputType.STDERR
  };

  private static final String JDK_HOME_STR = "!JDK_HOME!";
  //ERROR: JDWP Unable to get JNI 1.2 environment, jvm->GetEnv() return code = -2
  private static final Pattern JDI_BUG_OUTPUT_PATTERN_1 =
    Pattern.compile(
      "ERROR:\\s+JDWP\\s+Unable\\s+to\\s+get\\s+JNI\\s+1\\.2\\s+environment,\\s+jvm->GetEnv\\(\\)\\s+return\\s+code\\s+=\\s+-2\n");
  //JDWP exit error AGENT_ERROR_NO_JNI_ENV(183):  [../../../src/share/back/util.c:820]
  private static final Pattern JDI_BUG_OUTPUT_PATTERN_2 =
    Pattern.compile("JDWP\\s+exit\\s+error\\s+AGENT_ERROR_NO_JNI_ENV.*]\n");

  /** If a string containing one of the listed strings is written to {@link ProcessOutputType#STDERR}, the whole string is ignored. */
  private static final String[] IGNORED_IN_STDERR = {"Picked up _JAVA_OPTIONS:", "Picked up JAVA_TOOL_OPTIONS:"};

  private final Producer<String> myAppPath;
  private final Producer<String> myOutputPath;
  private Map<Key, StringBuffer> myBuffers;
  private String myTestName;

  private static String HOST_NAME = null;
  private static String CANONICAL_HOST_NAME = null;

  static {
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      HOST_NAME = localHost.getHostName();
      CANONICAL_HOST_NAME = localHost.getCanonicalHostName();
    }
    catch (UnknownHostException ignored) {
    }
  }

  public OutputChecker(Producer<String> appPath, Producer<String> outputPath) {
    myAppPath = appPath;
    myOutputPath = outputPath;
  }

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "SynchronizeOnThis"})
  public void init(String testName) {
    IdeaLogger.ourErrorsOccurred = null;

    myTestName = Character.toLowerCase(testName.charAt(0)) + testName.substring(1);
    synchronized (this) {
      myBuffers = new HashMap<>();
    }
  }

  @SuppressWarnings("SynchronizeOnThis")
  public void print(String s, Key outputType) {
    synchronized (this) {
      if (myBuffers != null) {
        if (outputType == ProcessOutputType.STDERR && ContainerUtil.exists(IGNORED_IN_STDERR, s::contains)) {
          return;
        }
        myBuffers.computeIfAbsent(outputType, k -> new StringBuffer()).append(s);
      }
    }
  }

  public void println(String s, Key outputType) {
    print(s + "\n", outputType);
  }

  public void checkValid(Sdk jdk) throws Exception {
    checkValid(jdk, false);
  }

  private File getOutFile(File outsDir, Sdk jdk, @Nullable File current, String prefix) {
    String name = myTestName + prefix;
    File res = new File(outsDir, name + ".out");
    if (current == null || res.exists()) {
      current = res;
    }
    JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
    for (int feature = version.getMaxLanguageLevel().feature(); feature > 6; feature--) {
      File outFile = new File(outsDir, name + ".jdk" + feature + ".out");
      if (outFile.exists()) {
        current = outFile;
        break;
      }
    }
    return current;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void checkValid(Sdk jdk, boolean sortClassPath) throws Exception {
    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }

    String actual = preprocessBuffer(buildOutputString(), sortClassPath);

    File outsDir = new File(myAppPath.produce(), "outs");
    assertTrue(outsDir.toString(), outsDir.exists() || outsDir.mkdirs());

    File outFile = getOutFile(outsDir, jdk, null, "");
    if (!outFile.exists()) {
      if (SystemInfo.isWindows) {
        outFile = getOutFile(outsDir, jdk, outFile, ".win");
      }
      else if (SystemInfo.isUnix) {
        outFile = getOutFile(outsDir, jdk, outFile, ".unx");
      }
    }

    if (!outFile.exists()) {
      FileUtil.writeToFile(outFile, actual);
      fail("Test file created " + outFile.getPath() + "\n" + "**************** Don't forget to put it into VCS! *******************");
    }
    else {
      String originalText = FileUtilRt.loadFile(outFile, CharsetToolkit.UTF8);
      String expected = StringUtilRt.convertLineSeparators(originalText);
      if (!expected.equals(actual)) {
        System.out.println("expected:");
        System.out.println(originalText);
        System.out.println("actual:");
        System.out.println(actual);

        int len = Math.min(expected.length(), actual.length());
        if (expected.length() != actual.length()) {
          System.out.println("Text sizes differ: expected " + expected.length() + " but actual: " + actual.length());
        }
        if (expected.length() > len) {
          System.out.println("Rest from expected text is: \"" + expected.substring(len) + "\"");
        }
        else if (actual.length() > len) {
          System.out.println("Rest from actual text is: \"" + actual.substring(len) + "\"");
        }

        throw new FileComparisonFailedError(null, expected, actual, outFile.getPath());
      }
    }
  }

  private synchronized String buildOutputString() {
    StringBuilder result = new StringBuilder();
    for (Key key : OUTPUT_ORDER) {
      StringBuffer buffer = myBuffers.get(key);
      if (buffer != null) {
        result.append(buffer);
      }
    }
    return result.toString();
  }

  private String preprocessBuffer(String buffer, boolean sortClassPath) throws Exception {
    Application application = ApplicationManager.getApplication();

    if (application == null) return buffer;

    return ReadAction.compute(() -> {
      String result = buffer;

      result = result.replace("\r\n", "\n");
      result = result.replace('\r', '\n');
      result = replaceAdditionalInOutput(result);
      result = replacePath(result, myAppPath.produce(), "!APP_PATH!");
      result = replacePath(result, myOutputPath.produce(), "!OUTPUT_PATH!");
      result = replacePath(result, JavaSdkUtil.getIdeaRtJarPath(), "!RT_JAR!");
      if (PluginManagerCore.isRunningFromSources()) {
        result = replacePath(result, DebuggerUtilsImpl.getIdeaRtPath(), "!RT_JAR!");
      }

      String junit4JarPaths = StringUtil.join(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit4"), File.pathSeparator);
      result = replacePath(result, junit4JarPaths, "!JUNIT4_JARS!");

      if (!StringUtil.isEmpty(CANONICAL_HOST_NAME)) {
        result = StringUtil.replace(result, CANONICAL_HOST_NAME, "!HOST_NAME!", true);
      }
      if (!StringUtil.isEmpty(HOST_NAME)) {
        result = StringUtil.replace(result, HOST_NAME, "!HOST_NAME!", true);
      }
      result = result.replace("127.0.0.1", "!HOST_NAME!");

      VirtualFile homeDirectory = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk().getHomeDirectory();
      assertNotNull(homeDirectory);
      result = replacePath(result, homeDirectory.getPath(), JDK_HOME_STR);

      File productionFile = new File(PathUtil.getJarPathForClass(OutputChecker.class));
      if (productionFile.isDirectory()) {
        result = replacePath(result, UriUtil.trimTrailingSlashes(productionFile.getParentFile().toURI().toString()), "!PRODUCTION_PATH!");
      }

      result = replacePath(result, PathManager.getHomePath(), "!IDEA_HOME!");
      result = result.replace("Process finished with exit code 255", "Process finished with exit code -1");

      result = result.replaceAll(" -javaagent:.*debugger-agent\\.jar", "");
      result = result.replaceAll(" -agentpath:\\S*memory_agent([\\w^\\s]*)?\\.\\S+", "");
      result = result.replaceAll("!HOST_NAME!:\\d*", "!HOST_NAME!:!HOST_PORT!");
      result = result.replaceAll("at '.*?'", "at '!HOST_NAME!:PORT_NAME!'");
      result = result.replaceAll("address: '.*?'", "address: '!HOST_NAME!:PORT_NAME!'");
      result = result.replaceAll("\"?file:.*AppletPage.*\\.html\"?", "file:!APPLET_HTML!");
      result = result.replaceAll("\"(!JDK_HOME!.*?)\"", "$1");
      result = result.replaceAll("\"(!APP_PATH!.*?)\"", "$1");
      result = result.replaceAll("\"(-D.*?)\"", "$1");  // unquote extra params
      result = result.replaceAll("-Didea.launcher.port=\\d*", "-Didea.launcher.port=!IDEA_LAUNCHER_PORT!");
      result = result.replaceAll("-Dfile.encoding=[\\w-]*", "-Dfile.encoding=!FILE_ENCODING!");
      // Since Java 18, these options are added automatically to avoid garbled text in console
      // See JdkCommandLineSetup::appendEncoding and IDEA-291006
      result = result.replace("-Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 ", "");
      result = result.replace("-Dkotlinx.coroutines.debug.enable.creation.stack.trace=false ", "");
      result = result.replace("-Ddebugger.agent.enable.coroutines=true ", "");
      result = result.replace("-Ddebugger.agent.support.throwable=false ", "");
      result = result.replaceAll("\\((.*):\\d+\\)", "($1:!LINE_NUMBER!)");

      result = fixSlashes(result, JDK_HOME_STR);
      result = result.replace("!JDK_HOME!\\bin\\java.exe", "!JDK_HOME!\\bin\\java");

      result = stripQuotesAroundClasspath(result);

      Matcher matcher = Pattern.compile("-classpath\\s+(\\S+)\\s+").matcher(result);
      while (matcher.find()) {
        String classPath = matcher.group(1);
        String[] classPathElements = classPath.split(File.pathSeparator);

        // combine all JDK .jars into one marker
        List<String> classpathRes = new ArrayList<>();
        boolean hasJdkJars = false;
        for (String element : classPathElements) {
          if (!element.startsWith(JDK_HOME_STR)) {
            classpathRes.add(element);
          }
          else {
            hasJdkJars = true;
          }
        }
        if (hasJdkJars) {
          classpathRes.add("!JDK_JARS!");
        }

        if (sortClassPath) {
          Collections.sort(classpathRes);
        }

        String sortedPath = StringUtil.join(classpathRes, ";");
        result = result.replace(" " + classPath + " ", " " + sortedPath + " ");
      }

      result = JDI_BUG_OUTPUT_PATTERN_1.matcher(result).replaceAll("");
      result = JDI_BUG_OUTPUT_PATTERN_2.matcher(result).replaceAll("");

      return result;
    });
  }

  protected static String replacePath(String result, String path, String replacement) {
    result = StringUtil.replace(result, FileUtil.toSystemDependentName(path), replacement, !SystemInfo.isFileSystemCaseSensitive);
    result = StringUtil.replace(result, FileUtil.toSystemIndependentName(path), replacement, !SystemInfo.isFileSystemCaseSensitive);
    return result;
  }

  private static String fixSlashes(String text, @SuppressWarnings("SameParameterValue") String jdkHomeMarker) {
    int commandLineStart = text.indexOf(jdkHomeMarker);
    while (commandLineStart != -1) {
      StringBuilder builder = new StringBuilder(text);
      int i = commandLineStart + 1;
      while (i < builder.length()) {
        char c = builder.charAt(i);
        if (c == '\n') break;
        if (c == File.separatorChar) builder.setCharAt(i, '\\');
        i++;
      }
      text = builder.toString();
      commandLineStart = text.indexOf(jdkHomeMarker, commandLineStart + 1);
    }
    return text;
  }

  protected String replaceAdditionalInOutput(String str) {
    return str;
  }

  // do not depend on spaces in classpath
  private static String stripQuotesAroundClasspath(String result) {
    String cp = "-classpath ";
    int cpIdx = 0;
    while (true) {
      cpIdx = result.indexOf(cp, cpIdx);
      if (cpIdx == -1) break;
      int spaceIdx = result.indexOf(" ", cpIdx + cp.length());
      if (spaceIdx == -1) break;
      result = result.substring(0, cpIdx) +
               cp +
               StringUtil.unquoteString(result.substring(cpIdx + cp.length(), spaceIdx)) +
               result.substring(spaceIdx);
      cpIdx += cp.length();
    }
    return result;
  }
}