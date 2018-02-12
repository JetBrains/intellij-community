/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.intellij.images.statistics;

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.*;

/**
 * @author gregsh
 */
public class BackgroundImagesUsageCollector extends AbstractProjectsUsagesCollector {
  private static final UsageDescriptor EDITOR = new UsageDescriptor("editor");
  private static final UsageDescriptor FRAME = new UsageDescriptor("frame");

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("images.ide.background");
  }

  @NotNull
  @Override
  protected Set<UsageDescriptor> mergeUsagesPostProcess(@NotNull Set<UsageDescriptor> usagesFromAllProjects) {
    JBIterable<UsageDescriptor> itt = JBIterable.from(usagesFromAllProjects);
    return usageSet(itt.find(o -> EDITOR.getKey().equals(o.getKey())) != null, 
                    itt.find(o -> FRAME.getKey().equals(o.getKey())) != null);
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    return usageSet(StringUtil.isNotEmpty(getBackgroundSpec(project, EDITOR_PROP)),
                    StringUtil.isNotEmpty(getBackgroundSpec(project, FRAME_PROP)));
  }

  @NotNull
  private static Set<UsageDescriptor> usageSet(boolean editor, boolean frame) {
    if (!editor && !frame) return Collections.emptySet();
    if (editor && frame) return ContainerUtil.newHashSet(EDITOR, FRAME);
    return Collections.singleton(editor ? EDITOR : FRAME);
  }
}
