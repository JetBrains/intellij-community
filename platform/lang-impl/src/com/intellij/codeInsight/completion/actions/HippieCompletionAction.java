/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class HippieCompletionAction extends BaseCodeInsightAction implements DumbAware {
  public HippieCompletionAction() {
    super(false);
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformedImpl(@NotNull Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.hippie");
    super.actionPerformedImpl(project, editor);
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new HippieWordCompletionHandler(true);
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }
}
