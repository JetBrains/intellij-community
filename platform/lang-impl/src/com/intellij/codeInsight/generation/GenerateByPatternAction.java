/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class GenerateByPatternAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    PatternProvider[] extensions = Extensions.getExtensions(PatternProvider.EXTENSION_POINT_NAME);
    e.getPresentation().setVisible(true);
    for (PatternProvider extension : extensions) {
      if (extension.isAvailable(e.getDataContext())) return;
    }
    e.getPresentation().setVisible(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PatternDescriptor[] patterns = new PatternDescriptor[0];
    PatternProvider[] extensions = Extensions.getExtensions(PatternProvider.EXTENSION_POINT_NAME);
    for (PatternProvider extension : extensions) {
      if (extension.isAvailable(e.getDataContext())) {
        patterns = ArrayUtil.mergeArrays(patterns, extension.getDescriptors());
      }
    }
    GenerateByPatternDialog dialog = new GenerateByPatternDialog(e.getProject(), patterns);
    if (dialog.showAndGet()) {
      dialog.getSelectedDescriptor().actionPerformed(e.getDataContext());
    }
  }
}
