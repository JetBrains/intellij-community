/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/2/12
 * Time: 5:01 PM
 */
public class WorkingSetUsageActionProvider implements UsageGroupingRuleProvider {
  private UsageGroupingRule[] myRules = new UsageGroupingRule[0];

  @NotNull
  @Override
  public UsageGroupingRule[] getActiveRules(Project project) {
    return myRules;
  }

  @NotNull
  @Override
  public AnAction[] createGroupingActions(UsageView view) {
    return new AnAction[]{new ImportUsagesAction()};
  }
}
