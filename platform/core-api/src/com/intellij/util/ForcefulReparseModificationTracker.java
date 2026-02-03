// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;

import java.util.Collection;

/**
 * Incremented on force reparse of files from {@link FileContentUtilCore#reparseFiles(Collection)}
 */
@Service
public final class ForcefulReparseModificationTracker extends SimpleModificationTracker {
  public static ModificationTracker getInstance() {
    return ApplicationManager.getApplication().getService(ForcefulReparseModificationTracker.class);
  }

  static void increment() {
    ((SimpleModificationTracker)getInstance()).incModificationCount();
  }
}
