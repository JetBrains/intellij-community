/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.usages;


import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class UsageViewManager {
  public static UsageViewManager getInstance (Project project) {
    return ServiceManager.getService(project, UsageViewManager.class);
  }

  @NotNull
  public abstract UsageView createUsageView(UsageTarget[] targets, Usage[] usages, UsageViewPresentation presentation, Factory<UsageSearcher> usageSearcherFactory);

  @NotNull
  public abstract UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation, Factory<UsageSearcher> factory);

  @NotNull
  public abstract UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation);

  @Nullable ("in case no usages found or usage view not shown for one usage")
  public abstract UsageView searchAndShowUsages(UsageTarget[] searchFor,
                                Factory<UsageSearcher> searcherFactory,
                                boolean showPanelIfOnlyOneUsage,
                                boolean showNotFoundMessage, UsageViewPresentation presentation,
                                UsageViewStateListener listener);

  public abstract void setCurrentSearchCancelled(boolean flag);

  public abstract boolean searchHasBeenCancelled();

  public interface UsageViewStateListener {
    void usageViewCreated(UsageView usageView);
    void findingUsagesFinished(final UsageView usageView);
  }

  public abstract void searchAndShowUsages(UsageTarget[] searchFor,
                           Factory<UsageSearcher> searcherFactory,
                           FindUsagesProcessPresentation processPresentation,
                           UsageViewPresentation presentation,
                           UsageViewStateListener listener);

  @Nullable
  public abstract UsageView getSelectedUsageView();
}
