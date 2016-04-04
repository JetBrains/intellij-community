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
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Jeka
 * @since Jan 3, 2002
 */
public class CompilerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompilerUtil");

  public static String quotePath(String path) {
    if(path != null && path.indexOf(' ') != -1) {
      path = path.replaceAll("\\\\", "\\\\\\\\");
      path = '"' + path + '"';
    }
    return path;
  }

  public static void refreshIOFiles(@NotNull final Collection<File> files) {
    if (!files.isEmpty()) {
      LocalFileSystem.getInstance().refreshIoFiles(files);
    }
  }

  public static void refreshIODirectories(@NotNull final Collection<File> files) {
    if (!files.isEmpty()) {
      LocalFileSystem.getInstance().refreshIoFiles(files, false, true, null);
    }
  }

  public static void refreshOutputDirectories(Collection<File> outputs, boolean async) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    List<VirtualFile> toRefresh = new ArrayList<VirtualFile>();

    int newDirectories = 0;
    for (File ioOutput : outputs) {
      VirtualFile output = fileSystem.findFileByIoFile(ioOutput);
      if (output != null) {
        toRefresh.add(output);
      }
      else if (ioOutput.exists()) {
        VirtualFile parent = fileSystem.refreshAndFindFileByIoFile(ioOutput.getParentFile());
        if (parent != null) {
          parent.getChildren();
          toRefresh.add(parent);
          newDirectories++;
        }
      }
    }
    if (newDirectories > 10) {
      LOG.info(newDirectories + " new output directories were created, refreshing their parents together to avoid too many rootsChange events");
      RefreshQueue.getInstance().refresh(async, false, null, toRefresh);
    }
    else {
      LOG.debug("Refreshing " + outputs.size() + " outputs");
      fileSystem.refreshIoFiles(outputs, async, false, null);
    }
  }

  public static void refreshIOFile(final File file) {
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (vFile != null) {
      vFile.refresh(false, false);
    }
  }

  public static void addSourceCommandLineSwitch(final Sdk jdk, LanguageLevel chunkLanguageLevel, @NonNls final List<String> commandLine) {
    final String versionString = jdk.getVersionString();
    if (StringUtil.isEmpty(versionString)) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
    }

    final LanguageLevel applicableLanguageLevel = getApplicableLanguageLevel(versionString, chunkLanguageLevel);
    if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_8)) {
      commandLine.add("-source");
      commandLine.add("8");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_7)) {
      commandLine.add("-source");
      commandLine.add("1.7");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_6)) {
      commandLine.add("-source");
      commandLine.add("1.6");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_5)) {
      commandLine.add("-source");
      commandLine.add("1.5");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_4)) {
      commandLine.add("-source");
      commandLine.add("1.4");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_3)) {
      if (!(isOfVersion(versionString, "1.3") || isOfVersion(versionString, "1.2") || isOfVersion(versionString, "1.1"))) {
        //noinspection HardCodedStringLiteral
        commandLine.add("-source");
        commandLine.add("1.3");
      }
    }
  }

  //todo[nik] rewrite using JavaSdkVersion#getMaxLanguageLevel
  @NotNull
  public static LanguageLevel getApplicableLanguageLevel(String versionString, @NotNull LanguageLevel languageLevel) {
    final boolean is8OrNewer = isOfVersion(versionString, "1.8") || isOfVersion(versionString, "8.0");
    final boolean is7OrNewer = is8OrNewer || isOfVersion(versionString, "1.7") || isOfVersion(versionString, "7.0");
    final boolean is6OrNewer = is7OrNewer || isOfVersion(versionString, "1.6") || isOfVersion(versionString, "6.0");
    final boolean is5OrNewer = is6OrNewer || isOfVersion(versionString, "1.5") || isOfVersion(versionString, "5.0");
    final boolean is4OrNewer = is5OrNewer || isOfVersion(versionString, "1.4");
    final boolean is3OrNewer = is4OrNewer || isOfVersion(versionString, "1.3");
    final boolean is2OrNewer = is3OrNewer || isOfVersion(versionString, "1.2");
    final boolean is1OrNewer = is2OrNewer || isOfVersion(versionString, "1.0") || isOfVersion(versionString, "1.1");

    if (!is1OrNewer) {
      // unknown jdk version, cannot say anything about the corresponding language level, so leave it unchanged
      return languageLevel;
    }
    // now correct the language level to be not higher than jdk used to compile
    if (LanguageLevel.JDK_1_8.equals(languageLevel) && !is8OrNewer) {
      languageLevel = LanguageLevel.JDK_1_7;
    }
    if (LanguageLevel.JDK_1_7.equals(languageLevel) && !is7OrNewer) {
      languageLevel = LanguageLevel.JDK_1_6;
    }
    if (LanguageLevel.JDK_1_6.equals(languageLevel) && !is6OrNewer) {
      languageLevel = LanguageLevel.JDK_1_5;
    }
    if (LanguageLevel.JDK_1_5.equals(languageLevel) && !is5OrNewer) {
      languageLevel = LanguageLevel.JDK_1_4;
    }
    if (LanguageLevel.JDK_1_4.equals(languageLevel) && !is4OrNewer) {
      languageLevel = LanguageLevel.JDK_1_3;
    }
    return languageLevel;
  }

  public static boolean isOfVersion(String versionString, String checkedVersion) {
    return versionString.contains(checkedVersion);
  }

  public static <T extends Throwable> void runInContext(CompileContext context, String title, ThrowableRunnable<T> action) throws T {
    ProgressIndicator indicator = context.getProgressIndicator();
    if (title != null) {
      indicator.pushState();
      indicator.setText(title);
    }
    try {
      action.run();
    }
    finally {
      if (title != null) {
        indicator.popState();
      }
    }
  }

  public static void logDuration(final String activityName, long duration) {
    LOG.info(activityName + " took " + duration + " ms: " + duration /60000 + " min " +(duration %60000)/1000 + "sec");
  }
}
