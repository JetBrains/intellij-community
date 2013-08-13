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

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.test.AbstractExternalSystemTest
import com.intellij.openapi.externalSystem.test.ExternalProjectBuilder
import com.intellij.openapi.externalSystem.test.ExternalSystemTestUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEntry;

/**
 * @author Denis Zhdanov
 * @since 8/8/13 5:17 PM
 */
public class ExternalProjectServiceTest extends AbstractExternalSystemTest {

  void 'test no duplicate library dependency is added on subsequent refresh when there is an unresolved library'() {
    DataNode<ProjectData> projectNode = buildExternalProjectInfo {
      project {
        module('module') {
          lib('lib1')
          lib('lib2', unresolved: true) } } }

    applyProjectState([projectNode, projectNode])

    def helper = ServiceManager.getService(ProjectStructureHelper.class)
    def module = helper.findIdeModule('module', project)
    assertNotNull(module)
    
    def facade = ServiceManager.getService(PlatformFacade.class)
    def entries = facade.getOrderEntries(module)
    def dependencies = [:].withDefault { 0 }
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        def name = (entry as LibraryOrderEntry).libraryName
        dependencies[name]++
      }
    }
    ExternalSystemTestUtil.assertMapsEqual(['lib1': 1, 'lib2': 1], dependencies)
  }

  void 'test changes in a project layout (content roots) could be detected on Refresh'() {

    String rootPath = ExternalSystemApiUtil.toCanonicalPath("/project1");

    DataNode<ProjectData> projectNodeInitial = buildExternalProjectInfo {
      project {
        module('module') {
          contentRoot(rootPath) {
            folder(type: ExternalSystemSourceType.TEST, path: rootPath + '/src/test/resources')
            folder(type: ExternalSystemSourceType.TEST, path: rootPath + '/src/test/java')
            folder(type: ExternalSystemSourceType.TEST, path: rootPath + '/src/test/groovy')
            folder(type: ExternalSystemSourceType.SOURCE, path: rootPath + '/src/main/resources')
            folder(type: ExternalSystemSourceType.SOURCE, path: rootPath + '/src/main/java')
            folder(type: ExternalSystemSourceType.SOURCE, path: rootPath + '/src/main/groovy')
            folder(type: ExternalSystemSourceType.EXCLUDED, path: rootPath + '/.gradle')
            folder(type: ExternalSystemSourceType.EXCLUDED, path: rootPath + '/build')
          } } }
    }

    DataNode<ProjectData> projectNodeRefreshed = buildExternalProjectInfo {
      project {
        module('module') {
          contentRoot(rootPath) {
            folder(type: ExternalSystemSourceType.TEST, path: rootPath + '/src/test/resources')
            folder(type: ExternalSystemSourceType.TEST, path: rootPath + '/src/test/java')
            folder(type: ExternalSystemSourceType.SOURCE, path: rootPath + '/src/main/resources')
            folder(type: ExternalSystemSourceType.SOURCE, path: rootPath + '/src/main/java')
            folder(type: ExternalSystemSourceType.EXCLUDED, path: rootPath + '/.gradle')
            folder(type: ExternalSystemSourceType.EXCLUDED, path: rootPath + '/build')
          } } }
    }

    applyProjectState([projectNodeInitial, projectNodeRefreshed])

    def helper = ServiceManager.getService(ProjectStructureHelper.class)
    def module = helper.findIdeModule('module', project)
    assertNotNull(module)

    def facade = ServiceManager.getService(PlatformFacade.class)
    def entries = facade.getOrderEntries(module)
    def folders = [:].withDefault { 0 }
    for (OrderEntry entry : entries) {
      if (entry instanceof ModuleSourceOrderEntry) {
        def contentEntry = (entry as ModuleSourceOrderEntry).getRootModel().getContentEntries().first()
        folders['source']+=contentEntry.sourceFolders.length
        folders['excluded']+=contentEntry.excludeFolders.length
      }
    }
    ExternalSystemTestUtil.assertMapsEqual(['source': 4, 'excluded': 2], folders)
  }
}
