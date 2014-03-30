/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.externalSystem.test.AbstractExternalSystemTest
/**
 * @author Denis Zhdanov
 * @since 8/12/13 3:02 PM
 */
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
    
    AbstractExternalSystemLocalSettings.State state = new AbstractExternalSystemLocalSettings.State()
    settings.fillState(state)
    settings.loadState(state)
    // There was a problem that all sub-projects (module-level) tasks were removed on project open.
    assertEquals(2, settings.availableTasks.size())
  }
}
