/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.test.AbstractExternalSystemTest

class ExternalSystemSettingsTest extends AbstractExternalSystemTest {
  void 'test available tasks are not skipped for multi-module external project'() {
    setupExternalProject {
      project {
        module('module1', externalConfigPath: 'root/module1') {
          task('module1-task') }
        module('module2', externalConfigPath: 'root/module2') {
          task('module2-task') } } }

    def settings = externalSystemManager.localSettings
    assertEquals(2, settings.availableTasks.size())
    
    settings.loadState(settings.getState())
    // There was a problem that all sub-projects (module-level) tasks were removed on project open.
    assertEquals(2, settings.availableTasks.size())
  }
}
