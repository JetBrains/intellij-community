// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.ide.warmup.WarmupConfigurationOfCLIConfigurator

class UnknownSdkInspectionWarmupConfiguration : WarmupConfigurationOfCLIConfigurator(UnknownSdkInspectionCommandLineConfigurator())