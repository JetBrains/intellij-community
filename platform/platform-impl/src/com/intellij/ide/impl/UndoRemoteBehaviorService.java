// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.util.EventListener;


@Experimental
@Internal
public class UndoRemoteBehaviorService {
  @Topic.AppLevel
  public static final Topic<UndoRemoteBehaviorListener> TOPIC = new Topic<>(UndoRemoteBehaviorListener.class, Topic.BroadcastDirection.TO_PARENT);

  public static boolean isSpeculativeUndoEnabled() {
    if (!Registry.is("ide.undo.frontend.if.possible", true) ||
        PlatformUtils.isRider() ||
        PlatformUtils.isCLion() ||
        PlatformUtils.isAppCode()) {
      return false;
    }
    var service = ApplicationManager.getApplication().getService(UndoRemoteBehaviorService.class);
    return service.isSpeculativeUndoAvailable();
  }

  public static boolean debugExperimentalFrontendUndo() {
    if (!isSpeculativeUndoEnabled()) {
      throw new IllegalStateException("speculative undo is disabled");
    }
    return Registry.is("ide.undo.frontend.if.possible.debug", true);
  }

  protected boolean isSpeculativeUndoAvailable() {
    return false;
  }

  public interface UndoRemoteBehaviorListener extends EventListener {
    void onAvailabilityChanged();
  }
}
