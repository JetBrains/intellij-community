// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.descriptors

fun ConfigFileInfoSet.toConfigFileItems(): MutableList<ConfigFileItem> =
  this.configFileInfos.map { configFileInfo -> ConfigFileItem(configFileInfo.metaData.id, configFileInfo.url) }.toMutableList()
