/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class ShowRecentFindUsagesGroup extends ActionGroup {
  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
    e.getPresentation().setVisible(project != null);
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || DumbService.isDumb(project)) return EMPTY_ARRAY;
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    List<ConfigurableUsageTarget> history = new ArrayList<>(findUsagesManager.getHistory().getAll());
    Collections.reverse(history);

    String description =
      ActionManager.getInstance().getAction(UsageViewImpl.SHOW_RECENT_FIND_USAGES_ACTION_ID).getTemplatePresentation().getDescription();

    List<AnAction> children = new ArrayList<>(history.size());
    for (final ConfigurableUsageTarget usageTarget : history) {
      if (!usageTarget.isValid()) {
        continue;
      }
      String text = usageTarget.getLongDescriptiveName();
      AnAction action = new AnAction(text, description, null) {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          findUsagesManager.rerunAndRecallFromHistory(usageTarget);
        }
      };
      children.add(action);
    }
    return children.toArray(new AnAction[children.size()]);
  }
}
