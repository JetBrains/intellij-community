// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase

class ModifiableModelsProviderTest: HeavyPlatformTestCase() {

  fun `test module rename correctly works with production on test modules config`() {
    val modifiableModelsProvider = IdeModifiableModelsProviderImpl(myProject)
    modifiableModelsProvider.setTestModuleProperties(myModule, "other-production-module")
    modifiableModelsProvider.modifiableModuleModel.renameModule(myModule, "new-name")

    runWriteAction { modifiableModelsProvider.commit() }

    val moduleByNewName = ModuleManager.getInstance(project).findModuleByName("new-name")
    TestCase.assertNotNull(moduleByNewName)

    assertEquals("messages", "other-production-module", TestModuleProperties.getInstance(moduleByNewName!!).productionModuleName)
  }
}
