/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2004
 * Time: 8:51:42 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class IncludeExcludeActionBase extends AnAction {
  private static Usage[] EMPTY_ARRAY = new Usage[0];
  protected abstract void process(Usage[] usages, UsageView usageView);

  protected UsageView getUsageView(DataContext context) {
    return (UsageView)context.getData(UsageView.USAGE_VIEW);
  }

  private Usage[] getUsages(DataContext context) {
    UsageView usageView = getUsageView(context);
    if (usageView == null) return EMPTY_ARRAY;
    Usage[] usages = (Usage[])context.getData(UsageView.USAGES);
    return usages == null ? EMPTY_ARRAY : usages;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getUsages(e.getDataContext()).length > 0);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    process(getUsages(dataContext), getUsageView(dataContext));
  }
}
