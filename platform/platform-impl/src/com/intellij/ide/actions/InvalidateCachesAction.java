// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.gist.GistManager;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

final class InvalidateCachesAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(InvalidateCachesAction.class);

  InvalidateCachesAction() {
    String text = ApplicationManager.getApplication().isRestartCapable() ? ActionsBundle.message("action.InvalidateCachesRestart.text")
                                                                         : ActionsBundle.message("action.InvalidateCaches.text");
    getTemplatePresentation().setText(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    boolean canRestart = app.isRestartCapable();

    var dialog = new InvalidateCachesDialog(e.getData(CommonDataKeys.PROJECT),
                                            canRestart,
                                            CachesInvalidator.EP_NAME.getExtensionList()
    );

    dialog.show();

    if (dialog.isOK()) {
      for (CachesInvalidator invalidator : dialog.getEnabledInvalidators()) {
        try {
          invalidator.invalidateCaches();
        }
        catch (Throwable t) {
          LOG.warn("Failed to invalidate caches with " + invalidator.getClass().getName() + ". " + t.getMessage(), t);
        }
      }
    }

    if (dialog.isOK() || dialog.isRestartOnly()) {
      app.restart(true);
    }
  }
}
