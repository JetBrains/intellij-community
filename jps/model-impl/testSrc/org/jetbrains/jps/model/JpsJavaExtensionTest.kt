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

import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType

class JpsJavaExtensionTest : JpsJavaModelTestCase() {
  fun testModule() {
    val module = addModule()
    val extension = javaService.getOrCreateModuleExtension(module)
    extension.outputUrl = "file://path"
    val moduleExtension = javaService.getModuleExtension(module)
    assertNotNull(moduleExtension)
    assertEquals("file://path", moduleExtension!!.outputUrl)
  }

  fun testDependency() {
    val module = myProject.addModule("m", JpsJavaModuleType.INSTANCE)
    val library = myProject.addLibrary("l", JpsJavaLibraryType.INSTANCE)
    val dependency = module.dependenciesList.addLibraryDependency(library)
    javaService.getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.TEST
    javaService.getOrCreateDependencyExtension(dependency).isExported = true

    val dependencies = assertOneElement(myProject.modules).dependenciesList.dependencies
    assertEquals(2, dependencies.size)
    val dep = dependencies[1]
    val extension = javaService.getDependencyExtension(dep)
    assertNotNull(extension)
    assertTrue(extension!!.isExported)
    assertSame(JpsJavaDependencyScope.TEST, extension.scope)
  }
}
