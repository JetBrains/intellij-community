/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public class CopyLineStatusRangeAction extends DumbAwareAction {
  private final LineStatusTrackerBase myLineStatusTracker;
  private final Range myRange;

  public CopyLineStatusRangeAction(@NotNull LineStatusTrackerBase lineStatusTracker, @NotNull Range range) {
    myLineStatusTracker = lineStatusTracker;
    myRange = range;
    ActionUtil.copyFrom(this, IdeActions.ACTION_COPY);
  }

  public void update(final AnActionEvent e) {
    boolean enabled = Range.DELETED == myRange.getType() || Range.MODIFIED == myRange.getType();
    e.getPresentation().setEnabled(myLineStatusTracker.isValid() && enabled);
  }

  public void actionPerformed(final AnActionEvent e) {
    final String content = myLineStatusTracker.getVcsContent(myRange) + "\n";
    CopyPasteManager.getInstance().setContents(new StringSelection(content));
  }
}
