// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.MalformedPatternException;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.ConfigurationException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import static com.intellij.compiler.options.CompilerOptionsFilter.Setting;

public final class CompilerUIConfigurable {

  static final Set<Setting> EXTERNAL_BUILD_SETTINGS = EnumSet.of(
    Setting.EXTERNAL_BUILD, Setting.AUTO_MAKE, Setting.PARALLEL_COMPILATION, Setting.REBUILD_MODULE_ON_DEPENDENCY_CHANGE,
    Setting.HEAP_SIZE, Setting.COMPILER_VM_OPTIONS
  );

  static String patternsToString(final String[] patterns) {
    final StringBuilder extensionsString = new StringBuilder();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }

  public static void applyResourcePatterns(String extensionString, final CompilerConfigurationImpl configuration)
    throws ConfigurationException {
    StringTokenizer tokenizer = new StringTokenizer(extensionString, ";", false);
    List<String[]> errors = new ArrayList<>();

    while (tokenizer.hasMoreTokens()) {
      String namePattern = tokenizer.nextToken();
      try {
        configuration.addResourceFilePattern(namePattern);
      }
      catch (MalformedPatternException e) {
        errors.add(new String[]{namePattern, e.getLocalizedMessage()});
      }
    }

    if (!errors.isEmpty()) {
      final StringBuilder patternsWithError = new StringBuilder();
      for (final Object error : errors) {
        String[] pair = (String[])error;
        patternsWithError.append("\n");
        patternsWithError.append(pair[0]);
        patternsWithError.append(": ");
        patternsWithError.append(pair[1]);
      }

      throw new ConfigurationException(
        JavaCompilerBundle.message("error.compiler.configurable.malformed.patterns", patternsWithError.toString()),
        JavaCompilerBundle.message("bad.resource.patterns.dialog.title")
      );
    }
  }
}
