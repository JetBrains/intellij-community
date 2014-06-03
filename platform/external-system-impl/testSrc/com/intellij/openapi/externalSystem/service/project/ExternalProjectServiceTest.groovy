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
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.test.AbstractExternalSystemTest
import com.intellij.openapi.externalSystem.test.ExternalSystemTestUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil

import static com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.*
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
    ExternalSystemTestUtil.assertMapsEqual(['Test_external_system_id: lib1': 1, 'Test_external_system_id: lib2': 1], dependencies)
  }

  void 'test changes in a project layout (content roots) could be detected on Refresh'() {

    String rootPath = ExternalSystemApiUtil.toCanonicalPath(project.basePath);

    def contentRoots = [
      (TEST): ['src/test/resources', '/src/test/java', 'src/test/groovy'],
      (SOURCE): ['src/main/resources', 'src/main/java', 'src/main/groovy'],
      (EXCLUDED): ['.gradle', 'build']
    ]

    def projectRootBuilder = {
      buildExternalProjectInfo {
        project {
          module {
            contentRoot(rootPath) {
              contentRoots.each { key, values -> values.each { folder(type: key, path: "$rootPath/$it") } }
            } } } } }

    DataNode<ProjectData> projectNodeInitial = projectRootBuilder()

    contentRoots[(SOURCE)].remove(0)
    contentRoots[(TEST)].remove(0)
    DataNode<ProjectData> projectNodeRefreshed = projectRootBuilder()

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

  void 'test library dependency with sources path added on subsequent refresh'() {

    def libBinPath = new File(projectDir, "bin_path");
    def libSrcPath = new File(projectDir, "source_path");
    def libDocPath = new File(projectDir, "doc_path");

    FileUtil.createDirectory(libBinPath);
    FileUtil.createDirectory(libSrcPath);
    FileUtil.createDirectory(libDocPath);

    applyProjectState([
      buildExternalProjectInfo {
        project {
          module('module') {
            lib('lib1', level: 'module', bin: [libBinPath.absolutePath]) } } },
      buildExternalProjectInfo {
        project {
          module('module') {
            lib('lib1', level: 'module', bin: [libBinPath.absolutePath], src: [libSrcPath.absolutePath]) } } },
      buildExternalProjectInfo {
        project {
          module('module') {
            lib('lib1', level: 'module', bin: [libBinPath.absolutePath], src: [libSrcPath.absolutePath],  doc: [libDocPath.absolutePath]) } } }
    ])

    def helper = ServiceManager.getService(ProjectStructureHelper.class)
    def module = helper.findIdeModule('module', project)
    assertNotNull(module)

    def facade = ServiceManager.getService(PlatformFacade.class)
    def entries = facade.getOrderEntries(module)
    def dependencies = [:].withDefault { 0 }
    entries.each { OrderEntry entry ->
      if (entry instanceof LibraryOrderEntry) {
        def name = (entry as LibraryOrderEntry).libraryName
        dependencies[name]++
        if ("Test_external_system_id: lib1".equals(name)) {
          def classesUrls = entry.getUrls(OrderRootType.CLASSES)
          assertEquals(1, classesUrls.length)
          assertTrue(classesUrls[0].endsWith("bin_path"))
          def sourceUrls = entry.getUrls(OrderRootType.SOURCES)
          assertEquals(1, sourceUrls.length)
          assertTrue(sourceUrls[0].endsWith("source_path"))
          def docUrls = entry.getUrls(JavadocOrderRootType.instance)
          assertEquals(1, docUrls.length)
          assertTrue(docUrls[0].endsWith("doc_path"))
        }
        else {
          fail()
        }
      }
    }
    ExternalSystemTestUtil.assertMapsEqual(['Test_external_system_id: lib1': 1], dependencies)
  }
}
