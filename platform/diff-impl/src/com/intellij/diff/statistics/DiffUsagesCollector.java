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
package com.intellij.diff.statistics;

import com.intellij.diff.impl.DiffSettingsHolder;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class DiffUsagesCollector extends UsagesCollector {
  public static final GroupDescriptor ID = GroupDescriptor.create("Diff");

  @NotNull
  public GroupDescriptor getGroupId() {
    return ID;
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    Set<UsageDescriptor> usages = ContainerUtil.newHashSet();

    processUsages(DiffPlaces.DEFAULT, usages);
    processUsages(DiffPlaces.CHANGES_VIEW, usages);
    processUsages(DiffPlaces.COMMIT_DIALOG, usages);

    DiffSettingsHolder.DiffSettings diffSettings = DiffSettingsHolder.getInstance().getSettings(null);
    usages.add(new UsageDescriptor("diff.DiffSettings.Default.IterateNextFile", diffSettings.isGoToNextFileOnNextDifference() ? 1 : 0));

    return usages;
  }

  private static void processUsages(@NotNull String place, @NotNull Set<UsageDescriptor> usages) {
    DiffSettingsHolder.DiffSettings diffSettings = DiffSettingsHolder.getInstance().getSettings(place);
    TextDiffSettingsHolder.TextDiffSettings textSettings = TextDiffSettingsHolder.getInstance().getSettings(place);

    usages.add(new UsageDescriptor("diff.TextDiffSettings.Default.IgnorePolicy." + textSettings.getIgnorePolicy().name(), 1));
    usages.add(new UsageDescriptor("diff.TextDiffSettings.Default.HighlightPolicy." + textSettings.getHighlightPolicy().name(), 1));
    usages.add(new UsageDescriptor("diff.TextDiffSettings.Default.ExpandByDefault", textSettings.isExpandByDefault() ? 1 : 0));

    List<String> toolOrder = diffSettings.getDiffToolsOrder();
    int defaultToolIndex = ContainerUtil.indexOf(toolOrder, SimpleDiffTool.class.getCanonicalName());
    int unifiedToolIndex = ContainerUtil.indexOf(toolOrder, UnifiedDiffTool.class.getCanonicalName());
    boolean isUnifiedDefault = unifiedToolIndex != -1 && unifiedToolIndex < defaultToolIndex;
    usages.add(new UsageDescriptor("diff.DiffSettings.Default.isUnifiedTool", isUnifiedDefault ? 1 : 0));
  }
}
