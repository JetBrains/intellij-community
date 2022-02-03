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
package com.intellij.refactoring.typeMigration;

import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeMigration.usageInfo.OverriddenUsageInfo;
import com.intellij.util.ui.UIUtil;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Dmitry Batkovich
 */
class MigrateGetterNameSetting {

  private final AtomicReference<Boolean> myGlobalValue = new AtomicReference<>();

  void askUserIfNeeded(final OverriddenUsageInfo info, final String newMethodName, final PsiType migrationReturnType) {
    final Boolean globalValue = myGlobalValue.get();
    if (globalValue == null) {
      final String currentName = ((PsiMethod)info.getElement()).getName();
      final String messageText = JavaBundle.message("type.migration.getter.rename.suggestion.text",
                                                    currentName,
                                                    newMethodName, 
                                                    migrationReturnType.getCanonicalText());
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        final Boolean globalValue1 = myGlobalValue.get();
        if (globalValue1 == null) {
          final int code = showChooserDialog(messageText);
          if (code == 0) {
            myGlobalValue.set(true);
            info.setMigrateMethodName(newMethodName);
          }
          else if (code == 1) {
            info.setMigrateMethodName(newMethodName);
          }
          else if (code == 2) {
            myGlobalValue.set(false);
          }
        }
        else if (globalValue1.equals(Boolean.TRUE)) {
          info.setMigrateMethodName(newMethodName);
        }
      });
    }
    else if (globalValue.equals(Boolean.TRUE)) {
      info.setMigrateMethodName(newMethodName);
    }
  }

  private static int showChooserDialog(@NlsContexts.DialogMessage String messageText) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return messageText.contains("dontMigrateName") ? 3 : 1;
    }
    else {
      return Messages.showIdeaMessageDialog(null, messageText, JavaRefactoringBundle.message("type.migration.action.name"),
                                            new String[]{
                                              JavaBundle.message("type.migration.getter.rename.suggestion.always.migrate.method.names"),
                                              Messages.getYesButton(),
                                              JavaBundle.message("type.migration.getter.rename.suggestion.never.migrate.method.names"), 
                                              Messages.getNoButton()}, 0, null, null);
    }
  }
}
