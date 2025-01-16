/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JpsJavaExtensionTest {
  lateinit var project: JpsProject

  @BeforeEach
  fun setUp() {
    project = JpsElementFactory.getInstance().createModel().project
  }

  @Test
  fun module() {
    val module = addModule()
    val extension = javaService.getOrCreateModuleExtension(module)
    extension.outputUrl = "file://path"
    val moduleExtension = javaService.getModuleExtension(module)
    assertNotNull(moduleExtension)
    assertEquals("file://path", moduleExtension!!.outputUrl)
  }

  @Test
  fun dependency() {
    val module = addModule()
    val library = project.addLibrary("l", JpsJavaLibraryType.INSTANCE)
    val dependency = module.dependenciesList.addLibraryDependency(library)
    javaService.getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.TEST
    javaService.getOrCreateDependencyExtension(dependency).isExported = true

    val dependencies = assertOneElement(project.modules).dependenciesList.dependencies
    assertEquals(2, dependencies.size)
    val dep = dependencies[1]
    val extension = javaService.getDependencyExtension(dep)
    assertNotNull(extension)
    assertTrue(extension!!.isExported)
    assertSame(JpsJavaDependencyScope.TEST, extension.scope)
  }
  
  private fun addModule(): JpsModule {
    return project.addModule("m", JpsJavaModuleType.INSTANCE)
  }
  
  private val javaService
    get() = JpsJavaExtensionService.getInstance()
}
