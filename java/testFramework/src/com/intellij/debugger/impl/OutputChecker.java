// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class OutputChecker {
  public static final Key[] OUTPUT_ORDER = {ProcessOutputTypes.SYSTEM, ProcessOutputTypes.STDOUT, ProcessOutputTypes.STDERR};

  private static final String JDK_HOME_STR = "!JDK_HOME!";
  //ERROR: JDWP Unable to get JNI 1.2 environment, jvm->GetEnv() return code = -2
  private static final Pattern JDI_BUG_OUTPUT_PATTERN_1 =
    Pattern.compile("ERROR:\\s+JDWP\\s+Unable\\s+to\\s+get\\s+JNI\\s+1\\.2\\s+environment,\\s+jvm->GetEnv\\(\\)\\s+return\\s+code\\s+=\\s+-2\n");
  //JDWP exit error AGENT_ERROR_NO_JNI_ENV(183):  [../../../src/share/back/util.c:820]
  private static final Pattern JDI_BUG_OUTPUT_PATTERN_2 =
    Pattern.compile("JDWP\\s+exit\\s+error\\s+AGENT_ERROR_NO_JNI_ENV.*]\n");

  private final String myAppPath;
  private final String myOutputPath;
  private Map<Key, StringBuffer> myBuffers;
  private String myTestName;

  public OutputChecker(String appPath, String outputPath) {
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
        StringBuffer buffer = myBuffers.get(outputType);
        if (buffer == null) {
          myBuffers.put(outputType, buffer = new StringBuffer());
        }
        buffer.append(s);
      }
    }
  }

  public void println(String s, Key outputType) {
    print(s + "\n", outputType);
  }

  public void checkValid(Sdk jdk) throws Exception {
    checkValid(jdk, false);
  }

  private File getOutFile(File outs, Sdk jdk, @Nullable File current, String prefix) {
    String name = myTestName + prefix;
    File res = new File(outs, name + ".out");
    if (current == null || res.exists()) {
      current = res;
    }
    if (JavaSdkUtil.isJdkAtLeast(jdk, JavaSdkVersion.JDK_1_9)) {
      File outFile = new File(outs, name + ".jdk9.out");
      if (outFile.exists()) {
        current = outFile;
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

    File outs = new File(myAppPath + File.separator + "outs");
    assert outs.exists() || outs.mkdirs() : outs;

    File outFile = getOutFile(outs, jdk, null, "");
    if (!outFile.exists()) {
      if (SystemInfo.isWindows) {
        outFile = getOutFile(outs, jdk, outFile, ".win");
      }
      else if (SystemInfo.isUnix) {
        outFile = getOutFile(outs, jdk, outFile, ".unx");
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

        assertEquals(originalText, actual);
      }
    }
  }

  public boolean contains(String str) {
    return buildOutputString().contains(str);
  }

  private synchronized String buildOutputString() {
    StringBuilder result = new StringBuilder();
    for (Key key : OUTPUT_ORDER) {
      StringBuffer buffer = myBuffers.get(key);
      if (buffer != null) {
        result.append(buffer.toString());
      }
    }
    return result.toString();
  }

  private String preprocessBuffer(String buffer, boolean sortClassPath) throws Exception {
    Application application = ApplicationManager.getApplication();

    if (application == null) return buffer;

    return application.runReadAction(new ThrowableComputable<String, Exception>() {
      @Override
      public String compute() throws UnknownHostException {
        String result = buffer;

        result = StringUtil.replace(result, "\r\n", "\n");
        result = StringUtil.replace(result, "\r", "\n");
        result = replaceAdditionalInOutput(result);
        result = replacePath(result, myAppPath, "!APP_PATH!");
        result = replacePath(result, myOutputPath, "!OUTPUT_PATH!");
        result = replacePath(result, JavaSdkUtil.getIdeaRtJarPath(), "!RT_JAR!");

        String junit4JarPaths = StringUtil.join(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit4"), File.pathSeparator);
        result = replacePath(result, junit4JarPaths, "!JUNIT4_JARS!");

        InetAddress localHost = InetAddress.getLocalHost();
        result = StringUtil.replace(result, localHost.getCanonicalHostName(), "!HOST_NAME!", true);
        result = StringUtil.replace(result, localHost.getHostName(), "!HOST_NAME!", true);
        result = StringUtil.replace(result, "127.0.0.1", "!HOST_NAME!", false);

        VirtualFile homeDirectory = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk().getHomeDirectory();
        assertNotNull(homeDirectory);
        result = replacePath(result, homeDirectory.getPath(), JDK_HOME_STR);

        File productionFile = new File(PathUtil.getJarPathForClass(OutputChecker.class));
        if (productionFile.isDirectory()) {
          result = replacePath(result, StringUtil.trimTrailing(productionFile.getParentFile().toURI().toString(), '/'), "!PRODUCTION_PATH!");
        }

        result = replacePath(result, PathManager.getHomePath(), "!IDEA_HOME!");
        result = StringUtil.replace(result, "Process finished with exit code 255", "Process finished with exit code -1");

        result = result.replaceAll(" -javaagent:.*debugger-agent\\.jar", "");
        result = result.replaceAll("!HOST_NAME!:\\d*", "!HOST_NAME!:!HOST_PORT!");
        result = result.replaceAll("at '.*?'", "at '!HOST_NAME!:PORT_NAME!'");
        result = result.replaceAll("address: '.*?'", "address: '!HOST_NAME!:PORT_NAME!'");
        result = result.replaceAll("\"?file:.*AppletPage.*\\.html\"?", "file:!APPLET_HTML!");
        result = result.replaceAll("\"(!JDK_HOME!.*?)\"", "$1");
        result = result.replaceAll("\"(!APP_PATH!.*?)\"", "$1");
        result = result.replaceAll("\"(-D.*?)\"", "$1");  // unquote extra params
        result = result.replaceAll("-Didea.launcher.port=\\d*", "-Didea.launcher.port=!IDEA_LAUNCHER_PORT!");
        result = result.replaceAll("-Dfile.encoding=[\\w\\d-]*", "-Dfile.encoding=!FILE_ENCODING!");
        result = result.replaceAll("\\((.*):\\d+\\)", "($1:!LINE_NUMBER!)");

        result = fixSlashes(result, JDK_HOME_STR);
        result = result.replaceAll("!JDK_HOME!\\\\bin\\\\java.exe", "!JDK_HOME!\\\\bin\\\\java");

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
          result = StringUtil.replace(result, classPath, sortedPath);
        }

        result = JDI_BUG_OUTPUT_PATTERN_1.matcher(result).replaceAll("");
        result = JDI_BUG_OUTPUT_PATTERN_2.matcher(result).replaceAll("");

        return result;
      }
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
      result = result.substring(0, cpIdx) + cp + StringUtil.unquoteString(result.substring(cpIdx + cp.length(), spaceIdx)) + result.substring(spaceIdx);
      cpIdx += cp.length();
    }
    return result;
  }
}