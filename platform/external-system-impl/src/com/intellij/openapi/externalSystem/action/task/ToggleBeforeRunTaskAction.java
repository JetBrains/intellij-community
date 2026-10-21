// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action.task;

import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;

final class ToggleBeforeRunTaskAction extends ToggleTaskActivationAction {
  ToggleBeforeRunTaskAction() {
    super(ExternalSystemTaskActivator.Phase.BEFORE_RUN);
  }
}
