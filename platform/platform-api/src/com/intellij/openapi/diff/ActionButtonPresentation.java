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
package com.intellij.openapi.diff;

import com.intellij.CommonBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

public abstract class ActionButtonPresentation {
  private final String myName;

  public static final ActionButtonPresentation APPLY = new ActionButtonPresentation(CommonBundle.getApplyButtonText()) {
    @Override
    public void run(DialogWrapper dialog) {
      dialog.close(DialogWrapper.OK_EXIT_CODE);
    }
  };

  public static final ActionButtonPresentation CANCEL_WITH_PROMPT = new ActionButtonPresentation("Revert") {
    @Override
    public void run(DialogWrapper dialog) {
      if (Messages.showYesNoDialog(dialog.getRootPane(),
                                   DiffBundle.message("merge.dialog.exit.without.applying.changes.confirmation.message"),
                                   DiffBundle.message("cancel.visual.merge.dialog.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      }
    }
  };

  public ActionButtonPresentation(final String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public abstract void run(DialogWrapper dialog);
}
