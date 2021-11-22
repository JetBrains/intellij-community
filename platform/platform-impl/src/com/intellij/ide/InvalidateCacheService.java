// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.actions.InvalidateCachesDialog;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class InvalidateCacheService {
  private static final Logger LOG = Logger.getInstance(InvalidateCacheService.class);

  public static void invalidateCachesAndRestart(@Nullable Project project) {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    boolean canRestart = app.isRestartCapable();

    var dialog = new InvalidateCachesDialog(project,
                                            canRestart,
                                            CachesInvalidator.EP_NAME.getExtensionList()
    );

    dialog.show();

    var invalidators = dialog.getSelectedInvalidators();
    if (dialog.isOK()) {
      for (CachesInvalidator invalidator : invalidators) {
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
