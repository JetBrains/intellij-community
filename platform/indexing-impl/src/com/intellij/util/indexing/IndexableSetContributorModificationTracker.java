// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.SimpleModificationTracker;
import org.jetbrains.annotations.ApiStatus;

@Service
@ApiStatus.Internal
public final class IndexableSetContributorModificationTracker extends SimpleModificationTracker {
  public static IndexableSetContributorModificationTracker getInstance() {
    return ApplicationManager.getApplication().getService(IndexableSetContributorModificationTracker.class);
  }

  public IndexableSetContributorModificationTracker() {
    IndexableSetContributor.EP_NAME.addChangeListener(() -> incModificationCount(), ApplicationManager.getApplication());
  }
}
