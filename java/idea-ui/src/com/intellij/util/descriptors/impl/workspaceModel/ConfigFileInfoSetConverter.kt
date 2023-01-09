// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.descriptors.impl.workspaceModel

import com.intellij.util.descriptors.ConfigFileInfoSet

object ConfigFileInfoSetConverter {
  fun ConfigFileInfoSet.toConfigFileItems(): MutableList<ConfigFileItem> =
    this.configFileInfos.map { configFileInfo -> ConfigFileItem(configFileInfo.metaData.id, configFileInfo.url) }.toMutableList()
}
