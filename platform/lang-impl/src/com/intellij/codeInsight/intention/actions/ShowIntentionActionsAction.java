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

package com.intellij.codeInsight.intention.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class ShowIntentionActionsAction extends BaseCodeInsightAction implements HintManagerImpl.ActionToIgnore {
  public ShowIntentionActionsAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new ShowIntentionActionsHandler();
  }
}
