/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.DashboardGroupingRule;
import org.jetbrains.annotations.NotNull;

/**
 * @author konstantin.aleev
 */
public class DashboardGrouper {
  @NotNull private final DashboardGroupingRule myRule;
  private boolean myEnabled = true;

  public DashboardGrouper(@NotNull DashboardGroupingRule rule) {
    myRule = rule;
  }

  @NotNull
  public DashboardGroupingRule getRule() {
    return myRule;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }
}
