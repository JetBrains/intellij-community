/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.util.*

class CommunityImageResourcesSanityTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return ImageResourcesTestBase.collectBadIcons(TestRoot.COMMUNITY)
    }
  }
}

class CommunityImageResourcesOptimumSizeTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return ImageResourcesTestBase.collectIconsWithNonOptimumSize(TestRoot.COMMUNITY)
    }
  }
}

class CommunityIconClassesTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return ImageResourcesTestBase.collectNonRegeneratedIconClasses(TestRoot.COMMUNITY)
    }
  }
}

@Ignore
class AllImageResourcesSanityTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return ImageResourcesTestBase.collectBadIcons(TestRoot.ALL, true)
    }
  }
}

@Ignore
class AllImageResourcesOptimumSizeTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return ImageResourcesTestBase.collectIconsWithNonOptimumSize(TestRoot.ALL, false, true)
    }
  }
}


@RunWith(Parameterized::class)
abstract class ImageResourcesTestBase {
  @JvmField @Parameter(value = 0) var testName: String? = null
  @JvmField @Parameter(value = 1) var exception: Throwable? = null

  @Test
  fun test() {
    if (exception != null) throw exception!!
  }

  enum class TestRoot { COMMUNITY, ULTIMATE, ALL }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun collectBadIcons(root: TestRoot,
                        ignoreSkipTag: Boolean = false): List<Array<Any?>> {
      val model = loadProjectModel(root)
      val modules = collectModules(root, model)
      val checker = MySanityChecker(File(PathManager.getHomePath()), ignoreSkipTag)
      modules.forEach {
        checker.check(it)
      }
      return createTestData(modules, checker.failures)
    }

    @JvmStatic
    @JvmOverloads
    fun collectIconsWithNonOptimumSize(root: TestRoot,
                                       iconsOnly: Boolean = true,
                                       ignoreSkipTag: Boolean = false): List<Array<Any?>> {
      val model = loadProjectModel(root)
      val modules = collectModules(root, model)
      val checker = MyOptimumSizeChecker(File(PathManager.getHomePath()), iconsOnly, ignoreSkipTag)
      modules.forEach {
        checker.checkOptimumSizes(it)
      }
      return createTestData(modules, checker.failures)
    }

    @JvmStatic
    fun collectNonRegeneratedIconClasses(root: TestRoot): List<Array<Any?>> {
      val model = loadProjectModel(root)
      val util = model.project.modules.find { it.name == "util" } ?: throw IllegalStateException("Can't load module 'util'")
      val modules = collectModules(root, model)

      val checker = MyIconClassFileChecker(File(PathManager.getHomePath()), util)
      modules.forEach {
        checker.checkIconClasses(it)
      }
      return createTestData(modules, checker.failures)
    }

    private fun createTestData(modules: List<JpsModule>, failures: Collection<FailedTest>): List<Array<Any?>> {
      val success = listOf(arrayOf<Any?>("success: processed ${modules.size} modules", null))
      val failedTests = failures
        .sortedWith(compareBy<FailedTest> { it.module }.thenBy { it.id }.thenBy { it.message })
        .map { arrayOf<Any?>(it.getTestName(), it.getException()) }
      return success + failedTests
    }

    private fun loadProjectModel(root: TestRoot): JpsModel {
      val home = getHomePath(root)
      val model = JpsElementFactory.getInstance().createModel()

      val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
      JpsProjectLoader.loadProject(model.project, pathVariables, home.path)

      return model
    }

    private fun collectModules(root: TestRoot,
                               model: JpsModel): List<JpsModule> {
      val home = getHomePath(root)
      return model.project.modules
        .filterNot { root == TestRoot.ULTIMATE && isCommunityModule(home, it) }
    }

    private fun getHomePath(root: TestRoot): File {
      val home = File(PathManager.getHomePath())

      if (root == TestRoot.COMMUNITY) {
        val community = File(home, "community")
        if (community.exists()) return community
      }
      return home
    }

    private fun isCommunityModule(home: File, module: JpsModule): Boolean {
      val community = File(home, "community")
      return module.sourceRoots.all { FileUtil.isAncestor(community, it.file, false) }
    }
  }
}

private class MySanityChecker(projectHome: File, ignoreSkipTag: Boolean) : ImageSanityCheckerBase(projectHome, ignoreSkipTag) {
  val failures = ArrayList<FailedTest>()

  override fun log(severity: ImageSanityCheckerBase.Severity,
                   message: String,
                   module: JpsModule,
                   images: Collection<ImagePaths>) {
    if (severity == Severity.INFO) return
    images.forEach { image ->
      failures.add(FailedTest(module, message, image))
    }
  }
}

private class MyOptimumSizeChecker(val projectHome: File, val iconsOnly: Boolean, val ignoreSkipTag: Boolean) {
  val failures = ArrayList<FailedTest>()

  fun checkOptimumSizes(module: JpsModule) {
    val allImages = ImageCollector(projectHome, iconsOnly, ignoreSkipTag).collect(module)
    val images = allImages.filter { it.file != null }

    images.forEach { image ->
      image.files.values.forEach { file ->
        val optimized = ImageSizeOptimizer.optimizeImage(file)
        if (optimized != null && !optimized.hasOptimumSize) {
          failures.add(FailedTest(module, "image size can be optimized: ${optimized.compressionStats}", image, file))
        }
      }
    }
  }
}

private class MyIconClassFileChecker(val projectHome: File, val util: JpsModule) {
  val failures = ArrayList<FailedTest>()

  fun checkIconClasses(module: JpsModule) {
    val generator = IconsClassGenerator(projectHome, util, false)
    generator.processModule(module)

    generator.getModifiedClasses().forEach { (module, file) ->
      failures.add(FailedTest(module, "image class file should be regenerated", file))
    }
  }
}

class FailedTest internal constructor(val module: String, val message: String, val id: String, val paths: List<String>) {
  internal constructor(module: JpsModule, message: String, image: ImagePaths, file: File) :
    this(module.name, message, image.id, listOf(file.absolutePath))

  internal constructor(module: JpsModule, message: String, image: ImagePaths) :
    this(module.name, message, image.id, image.files.values.map { it.absolutePath }.toList())

  internal constructor(module: JpsModule, message: String, file: File) :
    this(module.name, message, file.name, listOf(file.path))

  fun getTestName(): String = "'${module}' - $id - $message"
  fun getException(): Throwable = Exception("${message}\n\n${paths.joinToString("\n")}")
}