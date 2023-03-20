// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
public final class ExternalAnnotatorManager implements Disposable {
  public static ExternalAnnotatorManager getInstance() {
    return ApplicationManager.getApplication().getService(ExternalAnnotatorManager.class);
  }

  private final MergingUpdateQueue myExternalActivitiesQueue =
    new MergingUpdateQueue("ExternalActivitiesQueue", 300, true, MergingUpdateQueue.ANY_COMPONENT, this,
                           null, false)
      .usePassThroughInUnitTestMode();

  @Override
  public void dispose() {
  }

  public void queue(@NotNull Update update) {
    myExternalActivitiesQueue.queue(update);
  }
}
