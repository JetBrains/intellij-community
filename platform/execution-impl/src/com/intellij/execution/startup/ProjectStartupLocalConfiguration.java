// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.startup;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;

@Service(Service.Level.PROJECT)
@State(name = "ProjectStartupLocalConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
final class ProjectStartupLocalConfiguration extends ProjectStartupConfigurationBase {
}
