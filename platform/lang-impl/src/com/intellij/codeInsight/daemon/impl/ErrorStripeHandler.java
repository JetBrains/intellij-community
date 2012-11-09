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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.ex.ErrorStripeAdapter;
import com.intellij.openapi.editor.ex.ErrorStripeEvent;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ErrorStripeHandler extends ErrorStripeAdapter {
  private final Project myProject;

  public ErrorStripeHandler(Project project) {
    myProject = project;
  }

  @Override
  public void errorMarkerClicked(@NotNull ErrorStripeEvent e) {
    RangeHighlighter highlighter = e.getHighlighter();
    if (!highlighter.isValid()) return;
    HighlightInfo info = findInfo(highlighter);
    if (info != null) {
      GotoNextErrorHandler.navigateToError(myProject, e.getEditor(), info);
    }
  }

  private static HighlightInfo findInfo(final RangeHighlighter highlighter) {
    Object o = highlighter.getErrorStripeTooltip();
    if (o instanceof HighlightInfo) return (HighlightInfo)o;
    return null;
  }
}
