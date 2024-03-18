// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.ide.actions.InvalidateCachesDialog;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class InvalidateCacheService {
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
      invalidateCaches(invalidators::contains);
    }

    if (dialog.isOK() || dialog.isRestartOnly()) {
      app.restart(true);
    }
  }

  public static void invalidateCaches(Predicate<? super CachesInvalidator> isAllowedInvalidator) {
    for (CachesInvalidator invalidator : CachesInvalidator.EP_NAME.getExtensionList()) {
      if (isAllowedInvalidator.test(invalidator)) {
        try {
          invalidator.invalidateCaches();
        }
        catch (Throwable t) {
          LOG.warn("Failed to invalidate caches with " + invalidator.getClass().getName() + ". " + t.getMessage(), t);
        }
      }
    }
  }
}

