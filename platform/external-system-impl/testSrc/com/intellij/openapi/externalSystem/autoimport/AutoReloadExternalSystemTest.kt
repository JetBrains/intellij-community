// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.util.Ref

class AutoReloadExternalSystemTest : AutoReloadExternalSystemTestCase() {

  fun `test modification tracking disabled by ES plugin`() {
    val autoImportAwareCondition = Ref.create(true)
    testWithDummyExternalSystem(autoImportAwareCondition) { settingsFile ->
      settingsFile.appendString("println 'hello'")
      assertStateAndReset(numReload = 0, numReloadStarted = 0, numReloadFinished = 0, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, numReloadStarted = 1, numReloadFinished = 1, event = "project refresh")
      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, numReloadStarted = 0, numReloadFinished = 0, event = "modification")
      autoImportAwareCondition.set(false)
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, numReloadStarted = 0, numReloadFinished = 0, event = "import with inapplicable autoImportAware")
      autoImportAwareCondition.set(true)
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, numReloadStarted = 1, numReloadFinished = 1, event = "empty project refresh")
    }
  }
}