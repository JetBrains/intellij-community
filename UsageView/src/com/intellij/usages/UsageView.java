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

import javax.swing.*;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:13:28 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageView extends Disposable {
  /**
   * Returns {@link com.intellij.usages.UsageTarget} to look usages for
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  String USAGE_TARGETS = "usageTarget";

  /**
   * Returns {@link com.intellij.usages.Usage} which are selected in usage view
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  String USAGES = "usages";

  @SuppressWarnings({"HardCodedStringLiteral"})
  String USAGE_VIEW = "UsageView.new";

  void appendUsage(Usage usage);
  void removeUsage(Usage usage);
  void includeUsages(Usage[] usages);
  void excludeUsages(Usage[] usages);
  void selectUsages(Usage[] usages);

  void close();
  boolean isSearchInProgress();

  void addButtonToLowerPane(Runnable runnable, String text, char mnemonic);
  void addPerformOperationAction(Runnable processRunnable, String commandName, String cannotMakeString, String shortDescription, char mnemonic);
  UsageViewPresentation getPresentation();

  Set<Usage> getExcludedUsages();
  Set<Usage> getSelectedUsages();
  Set<Usage> getUsages();

  JComponent getComponent();

  int getUsagesCount();
}
