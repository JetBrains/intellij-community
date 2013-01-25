/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Random;

public class TestMessageBoxAction extends AnAction {
  private final Random myRandom = new Random();

  public TestMessageBoxAction() {
    super("Test message box");
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    int r = myRandom.nextInt(10);
    if (r < 3) {
      String message = wrap("Test error message.", r);
      Messages.showErrorDialog(message, "Test");
    }
    else if (r < 6) {
      String message = wrap("Test warning message.", r);
      Messages.showWarningDialog(message, "Test");
    }
    else {
      String message = wrap("Test info message.", r);
      Messages.showInfoMessage(message, "Test");
    }
  }

  private static String wrap(String s, int r) {
    return r % 2 == 0 ? s : "<html><body><i>" + StringUtil.repeat(s + "<br>", 10) + "</i></body></html>";
  }
}
