/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.unscramble;

import com.intellij.openapi.application.ClipboardAnalyzeListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class UnscrambleListener extends ClipboardAnalyzeListener {

  private static final Pattern STACKTRACE_LINE =
    Pattern.compile(
      "[\t]*at [[_a-zA-Z0-9]+\\.]+[_a-zA-Z$0-9]+\\.[a-zA-Z0-9_]+\\([A-Za-z0-9_]+\\.java:[\\d]+\\)+[ [~]*\\[[a-zA-Z0-9\\.\\:/]\\]]*");

  @Override
  public boolean canHandle(@NotNull String value) {
    value = UnscrambleDialog.normalizeText(value);
    int linesCount = 0;
    for (String line : value.split("\n")) {
      line = line.trim();
      if (line.length() == 0) continue;
      line = StringUtil.trimEnd(line, "\r");
      if (STACKTRACE_LINE.matcher(line).matches()) {
        linesCount++;
      }
      else {
        linesCount = 0;
      }
      if (linesCount > 2) return true;
    }
    return false;
  }

  @Override
  protected void handle(@NotNull Project project, @NotNull String value) {
    final UnscrambleDialog dialog = new UnscrambleDialog(project);
    dialog.createNormalizeTextAction().actionPerformed(null);
    if (!DumbService.isDumb(project)) {
      dialog.doOKAction();
    }
  }
}
