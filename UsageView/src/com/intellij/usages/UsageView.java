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
package com.intellij.usages;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public interface UsageView extends Disposable {
  /**
   * Returns {@link com.intellij.usages.UsageTarget} to look usages for
   */
  @NonNls String USAGE_TARGETS = "usageTarget";
  DataKey<UsageTarget[]> USAGE_TARGETS_KEY = DataKey.create("usageTarget");

  /**
   * Returns {@link com.intellij.usages.Usage} which are selected in usage view
   */
  @NonNls String USAGES = "usages";
  DataKey<Usage[]> USAGES_KEY = DataKey.create("usages");

  @NonNls String USAGE_VIEW = "UsageView.new";
  DataKey<UsageView> USAGE_VIEW_KEY = DataKey.create("UsageView.new");
  DataKey<UsageInfo> USAGE_INFO_KEY = DataKey.create("UsageInfo");

  void appendUsage(Usage usage);
  void removeUsage(Usage usage);
  void includeUsages(Usage[] usages);
  void excludeUsages(Usage[] usages);
  void selectUsages(Usage[] usages);

  void close();
  boolean isSearchInProgress();

  /**
   * @deprecated please specify mnemonic by prefixing the mnenonic character with an ampersand (&& for Mac-specific ampersands)
   */
  void addButtonToLowerPane(Runnable runnable, String text, char mnemonic);
  void addButtonToLowerPane(Runnable runnable, String text);

  void addPerformOperationAction(Runnable processRunnable, String commandName, String cannotMakeString, String shortDescription);

  UsageViewPresentation getPresentation();

  Set<Usage> getExcludedUsages();
  Set<Usage> getSelectedUsages();
  Set<Usage> getUsages();
  List<Usage> getSortedUsages();

  JComponent getComponent();

  int getUsagesCount();
}
