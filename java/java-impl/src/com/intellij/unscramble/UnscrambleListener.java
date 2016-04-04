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

import com.intellij.Patches;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;

import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class UnscrambleListener extends ApplicationActivationListener.Adapter {
  private static final int MAX_STACKTRACE_SIZE = 100 * 1024;
  private String stacktrace = null;

  @Override
  public void applicationActivated(final IdeFrame ideFrame) {
    final Runnable processClipboard = new Runnable() {
      @Override
      public void run() {
        final String clipboard = AnalyzeStacktraceUtil.getTextInClipboard();
        if (clipboard != null && clipboard.length() < MAX_STACKTRACE_SIZE && !clipboard.equals(stacktrace)) {
          stacktrace = clipboard;
          final Project project = ideFrame.getProject();
          if (project != null && isStacktrace(stacktrace)) {
            final UnscrambleDialog dialog = new UnscrambleDialog(project);
            dialog.createNormalizeTextAction().actionPerformed(null);
            if (!DumbService.isDumb(project)) {
              dialog.doOKAction();
            }
          }
        }
      }
    };

    if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) {
      //IDEA's clipboard is synchronized with the system clipboard on frame activation so we need to postpone clipboard processing
      new Alarm().addRequest(processClipboard, 300);
    }
    else {
      processClipboard.run();
    }
  }

  @Override
  public void applicationDeactivated(IdeFrame ideFrame) {
    if (SystemInfo.isMac) return;
    
    stacktrace = AnalyzeStacktraceUtil.getTextInClipboard();
  }

  private static final Pattern STACKTRACE_LINE =
    Pattern.compile("[\t]*at [[_a-zA-Z0-9]+\\.]+[_a-zA-Z$0-9]+\\.[a-zA-Z0-9_]+\\([A-Za-z0-9_]+\\.java:[\\d]+\\)+[ [~]*\\[[a-zA-Z0-9\\.\\:/]\\]]*");

  public static boolean isStacktrace(String stacktrace) {
    stacktrace = UnscrambleDialog.normalizeText(stacktrace);
    int linesCount = 0;
    for (String line : stacktrace.split("\n")) {
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
}
