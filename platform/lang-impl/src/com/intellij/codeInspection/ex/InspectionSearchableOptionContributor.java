// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

final class InspectionSearchableOptionContributor extends SearchableOptionContributor {
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    for (InspectionToolWrapper<?, ?> toolWrapper : InspectionToolRegistrar.getInstance().createTools()) {
      String hit = toolWrapper.getDisplayName();
      processor.addOptions(toolWrapper.getDisplayName(), toolWrapper.getShortName(), hit,
                           InspectionToolsConfigurable.ID,
                           InspectionToolsConfigurable.getInspectionsDisplayName(), false);

      for (String group : toolWrapper.getGroupPath()) {
        processor.addOptions(group, toolWrapper.getShortName(), hit, InspectionToolsConfigurable.ID,
                             InspectionToolsConfigurable.getInspectionsDisplayName(), false);
      }

      final String description = toolWrapper.loadDescription();
      if (description != null) {
        @NonNls String descriptionText = HTML_PATTERN.matcher(description).replaceAll(" ");
        processor.addOptions(descriptionText, toolWrapper.getShortName(), hit, InspectionToolsConfigurable.ID,
                             InspectionToolsConfigurable.getInspectionsDisplayName(), false);
      }
    }

  }
}
