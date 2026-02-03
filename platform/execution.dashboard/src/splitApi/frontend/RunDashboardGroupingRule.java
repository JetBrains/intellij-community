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
package com.intellij.platform.execution.dashboard.splitApi.frontend;

import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FrontendRunConfigurationNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action for grouping items in a run dashboard (services) tree.
 * Grouping rules are applied to dashboard nodes according to their order defined in plug-in configuration.
 */
@ApiStatus.Internal
public interface RunDashboardGroupingRule {
  ExtensionPointName<RunDashboardGroupingRule> GROUPING_RULE_EP_NAME = new ExtensionPointName<>("com.intellij.runDashboardGroupingRule");

  /**
   * @param node node which should be grouped by this grouping rule.
   * @return a group which node belongs to or {@code null} if node could not be grouped by this rule.
   */
  @Nullable
  RunDashboardGroup getGroup(FrontendRunConfigurationNode node);

  /**
   * Returns a unique identifier for the rule.
   *
   * @return the rule identifier.
   */
  @NotNull
  String getName();
}
