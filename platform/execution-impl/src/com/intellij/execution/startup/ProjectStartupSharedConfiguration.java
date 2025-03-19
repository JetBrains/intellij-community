// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.startup;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@Service(Service.Level.PROJECT)
@State(name = "ProjectStartupSharedConfiguration", storages = @Storage("startup.xml"))
final class ProjectStartupSharedConfiguration extends ProjectStartupConfigurationBase {
}
