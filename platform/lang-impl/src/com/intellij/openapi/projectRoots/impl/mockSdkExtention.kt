// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

private const val MOCK_JDK_DIR_NAME_PREFIX = "mockJDK-"
private const val MOCK_JDK_GROUP_ID_PREFIX = "mockjdk-base-java";

internal fun Sdk.isMockSdk(): Boolean {
  val homePathValue = homePath
  return homePathValue != null && (homePathValue.contains(MOCK_JDK_DIR_NAME_PREFIX) || homePathValue.contains(MOCK_JDK_GROUP_ID_PREFIX))
}