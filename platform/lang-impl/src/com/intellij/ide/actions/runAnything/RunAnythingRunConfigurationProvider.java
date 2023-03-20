// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ChooseRunConfigurationPopup.ItemWrapper;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public class RunAnythingRunConfigurationProvider extends com.intellij.ide.actions.runAnything.activity.RunAnythingRunConfigurationProvider {
  @NotNull
  @Override
  public Collection<ItemWrapper> getValues(@NotNull DataContext dataContext, @NotNull String pattern) {
    return sort(fetchProject(dataContext), pattern);
  }

  @Nullable
  @Override
  public String getHelpGroupTitle() {
    return null;
  }

  @NotNull
  @Override
  public String getCompletionGroupTitle() {
    return IdeBundle.message("run.anything.run.configurations.group.title");
  }

  private static Collection<ItemWrapper> sort(@NotNull Project project, @NotNull String pattern) {
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern).build();
    List<ItemWrapper> list = ChooseRunConfigurationPopup.createFlatSettingsList(project);
    TreeMap<Integer, ItemWrapper> map = new TreeMap<Integer, ItemWrapper>(Comparator.reverseOrder());
    for (ItemWrapper wrapper : list) {
      String name = wrapper.getText();
      FList<TextRange> fragments = matcher.matchingFragments(name);
      if (fragments != null) {
        int rest = fragments.isEmpty() ? 0 : name.length() - fragments.get(fragments.size() - 1).getEndOffset();
        map.put(matcher.matchingDegree(name, false, fragments) - rest, wrapper);
      }
    }
    return map.values();
  }

  @NotNull
  @Override
  public List<RunAnythingContext> getExecutionContexts(@NotNull DataContext dataContext) {
    return ContainerUtil.emptyList();
  }
}