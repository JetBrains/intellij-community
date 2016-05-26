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

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.*;

/**
 * @author gregsh
 */
public class BackgroundUsageCollector extends UsagesCollector {
  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("images.ide.background");
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    boolean editor = StringUtil.isNotEmpty(getBackgroundSpec(EDITOR_PROP));
    boolean ide = StringUtil.isNotEmpty(getBackgroundSpec(FRAME_PROP));
    return ContainerUtil.newHashSet(new UsageDescriptor("editor", editor ? 1 : 0), new UsageDescriptor("frame", ide ? 1 : 0));
  }

}
