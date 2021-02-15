// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.rules.ProjectModelRule
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths

internal val configurationStoreTestDataRoot: Path
  get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/configurationStore")

internal fun ProjectModelRule.saveProjectState() {
  runBlocking { project.stateStore.save() }
}