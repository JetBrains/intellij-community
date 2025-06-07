// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;


@Experimental
@Internal
public class UndoRemoteBehaviorService {

  public static boolean isExperimentalFrontendUndoEnabled() {
    var service = ApplicationManager.getApplication().getService(UndoRemoteBehaviorService.class);
    return service.isFrontendUndoEnabled();
  }

  public static boolean debugExperimentalFrontendUndo() {
    return isExperimentalFrontendUndoEnabled() &&
           Registry.is("ide.undo.frontend.if.possible.debug", true);
  }

  protected boolean isFrontendUndoEnabled() {
    return Registry.is("ide.undo.frontend.if.possible", true) &&
           !PlatformUtils.isRider();
  }
}
