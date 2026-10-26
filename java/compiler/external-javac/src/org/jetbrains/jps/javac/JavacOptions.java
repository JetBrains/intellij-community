// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds javac command line options shared by the JPS build and the IDE.
 */
@ApiStatus.Internal
public final class JavacOptions {
  public static final String PROC_NONE_OPTION = "-proc:none";
  public static final String PROCESSORPATH_OPTION = "-processorpath";
  public static final String PROCESSOR_MODULE_PATH_OPTION = "--processor-module-path";

  private JavacOptions() { }

  /**
   * @return true if annotation processing is enabled and corresponding options were added, false if profile is null or disabled
   */
  public static boolean addAnnotationProcessingOptions(List<? super String> options, @Nullable AnnotationProcessingConfiguration profile) {
    if (profile == null || !profile.isEnabled()) {
      options.add(PROC_NONE_OPTION);
      return false;
    }

    // configuring annotation processing
    if (!profile.isObtainProcessorsFromClasspath()) {
      final String processorsPath = profile.getProcessorPath();
      options.add(profile.isUseProcessorModulePath() ? PROCESSOR_MODULE_PATH_OPTION : PROCESSORPATH_OPTION);
      options.add(FileUtil.toSystemDependentName(processorsPath.trim()));
    }

    final Set<String> processors = profile.getProcessors();
    if (!processors.isEmpty()) {
      options.add("-processor");
      options.add(StringUtil.join(processors, ","));
    }

    for (Map.Entry<String, String> optionEntry : profile.getProcessorOptions().entrySet()) {
      options.add("-A" + optionEntry.getKey() + "=" + optionEntry.getValue());
    }
    return true;
  }
}
