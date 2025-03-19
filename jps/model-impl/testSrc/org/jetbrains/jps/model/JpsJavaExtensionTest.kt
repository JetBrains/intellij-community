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
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.invariantSeparatorsPathString

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

  @Test
  fun findSourceFile(@TempDir dir: Path) {
    val module = addModule()
    val resourcesPath = dir.resolve("resources").createDirectory()
    val resources = module.addSourceRoot(JpsPathUtil.pathToUrl(resourcesPath.invariantSeparatorsPathString), JavaResourceRootType.RESOURCE)
    val foo = resourcesPath.resolve("foo.txt").createFile()
    assertEquals(foo, javaService.findSourceFile(resources, "foo.txt"))
    assertEquals(foo, javaService.findSourceFile(resources, "/foo.txt"))

    val resourcesWithPrefixPath = dir.resolve("resourcesWithPrefix").createDirectory()
    val bar = resourcesWithPrefixPath.resolve("bar.txt").createFile()
    val baz = resourcesWithPrefixPath.resolve("bar").createDirectory().resolve("baz.txt").createFile()
    val resourcesWithPrefix = module.addSourceRoot(JpsPathUtil.pathToUrl(resourcesWithPrefixPath.invariantSeparatorsPathString), 
                                                   JavaResourceRootType.RESOURCE, 
                                                   JavaResourceRootProperties("prefix", false))
    assertEquals(bar, javaService.findSourceFile(resourcesWithPrefix, "prefix/bar.txt"))
    assertEquals(baz, javaService.findSourceFile(resourcesWithPrefix, "prefix/bar/baz.txt"))
    assertNull(javaService.findSourceFile(resourcesWithPrefix, "bar.txt"))

    val srcWithPrefixPath = dir.resolve("srcWithPrefix").createDirectory()
    val javaClass = srcWithPrefixPath.resolve("bar").createDirectory().resolve("Baz.java").createFile()
    val srcWithPrefix = module.addSourceRoot(JpsPathUtil.pathToUrl(srcWithPrefixPath.invariantSeparatorsPathString),
                                             JavaSourceRootType.SOURCE,
                                             JavaSourceRootProperties("com.foo", false))
    assertEquals(javaClass, javaService.findSourceFile(srcWithPrefix, "com/foo/bar/Baz.java"))
    assertNull(javaService.findSourceFile(srcWithPrefix, "bar/Baz.java"))
  }

  private fun addModule(): JpsModule {
    return project.addModule("m", JpsJavaModuleType.INSTANCE)
  }
  
  private val javaService
    get() = JpsJavaExtensionService.getInstance()
}
