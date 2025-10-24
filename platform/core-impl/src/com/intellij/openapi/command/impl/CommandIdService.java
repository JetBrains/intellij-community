// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface CommandIdService {

  static @Nullable CommandIdService getInstance() {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      CommandIdService service = application.getService(CommandIdService.class);
      return service;
    }
    return null;
  }

  static void advanceCommandId() {
    CommandIdService service = getInstance();
    if (service != null) {
      service._advanceCommandId();
    }
  }

  static void advanceTransparentCommandId() {
    CommandIdService service = getInstance();
    if (service != null) {
      service._advanceTransparentCommandId();
    }
  }

  static @Nullable CommandId currCommandId() {
    CommandIdService service = getInstance();
    if (service != null) {
      return service._currCommandId();
    }
    return null;
  }

  static void setForcedCommand(@Nullable CommandId commandId) {
    CommandIdService service = getInstance();
    if (service != null) {
      service._setForcedCommand(commandId);
    }
  }

  void _advanceCommandId();

  void _advanceTransparentCommandId();

  @NotNull CommandId _currCommandId();

  void _setForcedCommand(@Nullable CommandId commandId);
}
