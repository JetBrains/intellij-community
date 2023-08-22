// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SimpleModificationTracker;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class IndexableSetContributorModificationTracker extends SimpleModificationTracker {
  public static IndexableSetContributorModificationTracker getInstance() {
    return ApplicationManager.getApplication().getService(IndexableSetContributorModificationTracker.class);
  }

  public IndexableSetContributorModificationTracker() {
    IndexableSetContributor.EP_NAME.addChangeListener(() -> incModificationCount(), ApplicationManager.getApplication());
  }
}
