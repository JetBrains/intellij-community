// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action.task;

import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;

final class ToggleBeforeSyncTaskAction extends ToggleTaskActivationAction {
  ToggleBeforeSyncTaskAction() {
    super(ExternalSystemTaskActivator.Phase.BEFORE_SYNC);
  }
}
