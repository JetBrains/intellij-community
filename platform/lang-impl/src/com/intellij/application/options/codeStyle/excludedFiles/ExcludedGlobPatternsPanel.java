// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.dsl.builder.Row;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ExcludedGlobPatternsPanel {

  public static final String PATTERN_SEPARATOR = ";";

  private ExcludedGlobPatternsPanel() {
  }

  public static String getPatternsText(@NotNull CodeStyleSettings settings, @NotNull Row conversionMessageRow) {
    List<String> patterns =
      new ArrayList<>(ContainerUtil.map(settings.getExcludedFiles().getDescriptors(GlobPatternDescriptor.TYPE), d -> d.getPattern()));
    List<String> convertedPatterns = getConvertedPatterns(settings);
    conversionMessageRow.visible(!convertedPatterns.isEmpty());
    patterns.addAll(convertedPatterns);
    return StringUtil.join(patterns, PATTERN_SEPARATOR);
  }

  private static @NotNull List<String> getConvertedPatterns(@NotNull CodeStyleSettings settings) {
    return settings.getExcludedFiles().getDescriptors(NamedScopeDescriptor.NAMED_SCOPE_TYPE).stream()
      .map(descriptor -> NamedScopeToGlobConverter.convert((NamedScopeDescriptor)descriptor))
      .filter(descriptor -> descriptor != null)
      .map(descriptor -> descriptor.getPattern())
      .collect(Collectors.toList());
  }

  public static List<FileSetDescriptor> getDescriptors(ExpandableTextField myPatternsField) {
    String patternsText = myPatternsField.getText();
    return toStringList(patternsText).stream()
      .sorted()
      .map(s -> new GlobPatternDescriptor(s)).collect(Collectors.toList());
  }

  public static List<String> toStringList(@NotNull String patternsText) {
    return StringUtil.isEmpty(patternsText)
           ? Collections.emptyList()
           : Arrays.stream(patternsText.split(PATTERN_SEPARATOR))
             .map(s -> s.trim())
             .filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }
}
