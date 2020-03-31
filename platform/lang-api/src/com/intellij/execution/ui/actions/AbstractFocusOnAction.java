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

package com.intellij.execution.ui.actions;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

public class AbstractFocusOnAction extends BaseViewAction implements Toggleable {
  @NotNull
  private final String myCondition;

  public AbstractFocusOnAction(@NotNull String condition) {
    myCondition = condition;
  }

  @Override
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    final boolean visible = content.length == 1;
    e.getPresentation().setVisible(visible);
    if (visible) {
      Toggleable.setSelected(e.getPresentation(), isToFocus(context, content));
    }
  }

  private boolean isToFocus(final ViewContext context, final Content[] content) {
    return context.getRunnerLayoutUi().getOptions().isToFocus(content[0], myCondition);
  }

  @Override
  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    final boolean toFocus = isToFocus(context, content);
    context.getRunnerLayoutUi().getOptions().setToFocus(toFocus ? null : content[0], myCondition);
  }
}
