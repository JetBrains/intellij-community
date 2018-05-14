// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.activity.RunAnythingActivityProvider;
import com.intellij.ide.actions.runAnything.activity.RunAnythingRecentProjectProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RecentProjectHelpGroup extends RunAnythingHelpGroup {
  @NotNull
  @Override
  public String getTitle() {
    return IdeBundle.message("run.anything.recent.project.help.group.title");
  }

  @NotNull
  @Override
  public Collection<RunAnythingActivityProvider> getProviders() {
    return ContainerUtil.immutableSingletonList(RunAnythingActivityProvider.EP_NAME.findExtension(RunAnythingRecentProjectProvider.class));
  }
}