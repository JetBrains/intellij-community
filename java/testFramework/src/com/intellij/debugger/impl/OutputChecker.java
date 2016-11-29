/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OutputChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.OutputChecker");
  private static final String JDK_HOME_STR = "!JDK_HOME!";

  protected final String myAppPath;
  private final String myOutputPath;

  public static final Key[] OUTPUT_ORDER = new Key[] {
    ProcessOutputTypes.SYSTEM, ProcessOutputTypes.STDOUT, ProcessOutputTypes.STDERR
  };
  private Map<Key, StringBuffer> myBuffers;
  protected String myTestName;


  //ERROR: JDWP Unable to get JNI 1.2 environment, jvm->GetEnv() return code = -2
  private static final Pattern JDI_BUG_OUTPUT_PATTERN_1 =
    Pattern.compile("ERROR\\:\\s+JDWP\\s+Unable\\s+to\\s+get\\s+JNI\\s+1\\.2\\s+environment,\\s+jvm-\\>GetEnv\\(\\)\\s+return\\s+code\\s+=\\s+-2\n");
  //JDWP exit error AGENT_ERROR_NO_JNI_ENV(183):  [../../../src/share/back/util.c:820]
  private static final Pattern JDI_BUG_OUTPUT_PATTERN_2 =
    Pattern.compile("JDWP\\s+exit\\s+error\\s+AGENT_ERROR_NO_JNI_ENV.*\\]\n");

  public OutputChecker(String appPath, String outputPath) {
    myAppPath = appPath;
    myOutputPath = outputPath;
  }

  public void init(String testName) {
    IdeaLogger.ourErrorsOccurred = null;

    testName = Character.toLowerCase(testName.charAt(0)) + testName.substring(1);
    myTestName = testName;
    synchronized (this) {
      myBuffers = new HashMap<>();
    }
  }

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

  public void checkValid(Sdk jdk, boolean sortClassPath) throws Exception {
    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }

    String actual = preprocessBuffer(jdk, buildOutputString(), sortClassPath);

    File outs = new File(myAppPath + File.separator + "outs");
    assert outs.exists() || outs.mkdirs() : outs;

    File outFile = new File(outs, myTestName + ".out");
    if (!outFile.exists()) {
      if (SystemInfo.isWindows) {
        final File winOut = new File(outs, myTestName + ".win.out");
        if (winOut.exists()) {
          outFile = winOut;
        }
      }
      else if (SystemInfo.isUnix) {
        final File unixOut = new File(outs, myTestName + ".unx.out");
        if (unixOut.exists()) {
          outFile = unixOut;
        }
      }
    }

    if (!outFile.exists()) {
      FileUtil.writeToFile(outFile, actual);
      LOG.error("Test file created " + outFile.getPath() + "\n" + "**************** Don't forget to put it into VCS! *******************");
    }
    else {
      String originalText = FileUtilRt.loadFile(outFile, CharsetToolkit.UTF8);
      String expected = StringUtilRt.convertLineSeparators(originalText);
      if (!expected.equals(actual)) {
        System.out.println("expected:");
        System.out.println(originalText);
        System.out.println("actual:");
        System.out.println(actual);

        final int len = Math.min(expected.length(), actual.length());
        if (expected.length() != actual.length()) {
          System.out.println("Text sizes differ: expected " + expected.length() + " but actual: " + actual.length());
        }
        if (expected.length() > len) {
          System.out.println("Rest from expected text is: \"" + expected.substring(len) + "\"");
        }
        else if (actual.length() > len) {
          System.out.println("Rest from actual text is: \"" + actual.substring(len) + "\"");
        }

        Assert.assertEquals(originalText, actual);
      }
    }
  }

  private synchronized String buildOutputString() {
    final StringBuilder result = new StringBuilder();
    for (Key key : OUTPUT_ORDER) {
      final StringBuffer buffer = myBuffers.get(key);
      if (buffer != null) {
        result.append(buffer.toString());
      }
    }
    return result.toString();
  }

  private String preprocessBuffer(final Sdk testJdk, final String buffer, final boolean sortClassPath) throws Exception {
    Application application = ApplicationManager.getApplication();

    if (application == null) return buffer;

    return application.runReadAction(new ThrowableComputable<String, Exception>() {
      @Override
      public String compute() throws UnknownHostException {
        String internalJdkHome = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk().getHomeDirectory().getPath();
        //System.out.println("internalJdkHome = " + internalJdkHome);

        String result = buffer;
        //System.out.println("Original Output = " + result);
        final boolean shouldIgnoreCase = !SystemInfo.isFileSystemCaseSensitive;

        result = StringUtil.replace(result, "\r\n", "\n");
        result = StringUtil.replace(result, "\r", "\n");
        result = replaceAdditionalInOutput(result);
        result = StringUtil.replace(result, myAppPath, "!APP_PATH!", shouldIgnoreCase);
        result = StringUtil.replace(result, myOutputPath, "!OUTPUT_PATH!", shouldIgnoreCase);
        result = StringUtil.replace(result, FileUtil.toSystemIndependentName(myOutputPath), "!OUTPUT_PATH!", shouldIgnoreCase);
        result = StringUtil.replace(result, FileUtil.toSystemIndependentName(myAppPath), "!APP_PATH!", shouldIgnoreCase);
        result = StringUtil.replace(result, JavaSdkUtil.getIdeaRtJarPath(), "!RT_JAR!", shouldIgnoreCase);
        result = StringUtil.replace(result, JavaSdkUtil.getJunit4JarPath(), "!JUNIT4_JAR!", shouldIgnoreCase);
        result = StringUtil.replace(result, InetAddress.getLocalHost().getCanonicalHostName(), "!HOST_NAME!", shouldIgnoreCase);
        result = StringUtil.replace(result, InetAddress.getLocalHost().getHostName(), "!HOST_NAME!", shouldIgnoreCase);
        result = StringUtil.replace(result, "127.0.0.1", "!HOST_NAME!", shouldIgnoreCase);
        result = StringUtil.replace(result, JavaSdkUtil.getIdeaRtJarPath().replace('/', File.separatorChar), "!RT_JAR!", shouldIgnoreCase);
        result = StringUtil.replace(result, FileUtil.toSystemDependentName(internalJdkHome), JDK_HOME_STR, shouldIgnoreCase);
        result = StringUtil.replace(result, internalJdkHome, JDK_HOME_STR, shouldIgnoreCase);
        result = StringUtil.replace(result, PathManager.getHomePath(), "!IDEA_HOME!", shouldIgnoreCase);
        result = StringUtil.replace(result, "Process finished with exit code 255", "Process finished with exit code -1");

        //          result = result.replaceAll(" +\n", "\n");
        result = result.replaceAll("!HOST_NAME!:\\d*", "!HOST_NAME!:!HOST_PORT!");
        result = result.replaceAll("at \\'.*?\\'", "at '!HOST_NAME!:PORT_NAME!'");
        result = result.replaceAll("address: \\'.*?\\'", "address: '!HOST_NAME!:PORT_NAME!'");
        result = result.replaceAll("\"?file:.*AppletPage.*\\.html\"?", "file:!APPLET_HTML!");
        result = result.replaceAll("\"(!JDK_HOME!.*?)\"", "$1");
        result = result.replaceAll("\"(!APP_PATH!.*?)\"", "$1");

        // unquote extra params
        result = result.replaceAll("\"(-D.*?)\"", "$1");

        result = result.replaceAll("-Didea.launcher.port=\\d*", "-Didea.launcher.port=!IDEA_LAUNCHER_PORT!");
        result = result.replaceAll("-Dfile.encoding=[\\w\\d-]*", "-Dfile.encoding=!FILE_ENCODING!");
        result = result.replaceAll("\\((.*)\\:\\d+\\)", "($1:!LINE_NUMBER!)");

        result = fixSlashes(result, JDK_HOME_STR);

        result = stripQuotesAroundClasspath(result);

        final Matcher matcher = Pattern.compile("-classpath\\s+(\\S+)\\s+").matcher(result);
        while (matcher.find()) {
          final String classPath = matcher.group(1);
          final String[] classPathElements = classPath.split(File.pathSeparator);

          // Combine all JDK jars into one marker
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

          final String sortedPath = StringUtil.join(classpathRes, ";");
          result = StringUtil.replace(result, classPath, sortedPath);
        }

        result = JDI_BUG_OUTPUT_PATTERN_1.matcher(result).replaceAll("");
        result = JDI_BUG_OUTPUT_PATTERN_2.matcher(result).replaceAll("");

        return result;
      }
    });
  }

  @NotNull
  private static String fixSlashes(String text, final String jdkHomeMarker) {
    int commandLineStart = text.indexOf(jdkHomeMarker);
    while (commandLineStart != -1) {
      final StringBuilder builder = new StringBuilder(text);
      int i = commandLineStart + 1;
      while (i < builder.length()) {
        char c = builder.charAt(i);
        if (c == '\n') break;
        else if (c == File.separatorChar) builder.setCharAt(i, '\\');
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

  //do not depend on spaces in jdk path
  private static String stripQuotesAroundClasspath(String result) {
    final String clsp = "-classpath ";
    int clspIdx = 0;
    while (true) {
      clspIdx = result.indexOf(clsp, clspIdx);
      if (clspIdx <= -1) {
        break;
      }

      final int spaceIdx = result.indexOf(" ", clspIdx + clsp.length());
      if (spaceIdx > -1) {
        result = result.substring(0, clspIdx) +
                 clsp +
                 StringUtil.stripQuotesAroundValue(result.substring(clspIdx + clsp.length(), spaceIdx)) +
                 result.substring(spaceIdx);
        clspIdx += clsp.length();
      } else {
        break;
      }
    }
    return result;
  }
}
