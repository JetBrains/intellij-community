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
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Set;

import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.*;

/**
 * @author gregsh
 */
public class BackgroundImagesUsageCollector extends AbstractProjectsUsagesCollector {
  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("images.ide.background");
  }

  @NotNull
  @Override
  protected Set<UsageDescriptor> mergeUsagesPostProcess(@NotNull Set<UsageDescriptor> usagesFromAllProjects) {
    // join usages from all projects
    HashMap<String, UsageDescriptor> map = ContainerUtil.newHashMap();
    for (UsageDescriptor descriptor : usagesFromAllProjects) {
      String key = descriptor.getKey();
      int idx = key.indexOf(' ');
      key = idx > 0 ? key.substring(0, idx) : key;
      UsageDescriptor existing = map.get(key);
      if (existing == null || existing.getValue() == 0) {
        map.put(key, new UsageDescriptor(key, descriptor.getValue()));
      }
    }
    return ContainerUtil.newHashSet(map.values());
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    boolean editor = StringUtil.isNotEmpty(getBackgroundSpec(project, EDITOR_PROP));
    boolean frame = StringUtil.isNotEmpty(getBackgroundSpec(project, FRAME_PROP));
    return ContainerUtil.newHashSet(new UsageDescriptor("editor", editor ? 1 : 0), new UsageDescriptor("frame", frame ? 1 : 0));
  }
}
