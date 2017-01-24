/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.build.scripts

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.model.JpsElementFactory
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
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy

class CommunityImageResourcesSanityTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any>> {
      return ImageResourcesTestBase.collectBadIcons()
    }
  }
}

class CommunityImageResourcesOptimumSizeTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any>> {
      return ImageResourcesTestBase.collectIconsWithNonOptimumSize()
    }
  }
}

@Ignore
class AllImageResourcesSanityTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any>> {
      return ImageResourcesTestBase.collectBadIcons(true)
    }
  }
}

@Ignore
class AllImageResourcesOptimumSizeTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any>> {
      return ImageResourcesTestBase.collectIconsWithNonOptimumSize(false, true)
    }
  }
}


@RunWith(Parameterized::class)
abstract class ImageResourcesTestBase {
  @Parameter(value = 0) lateinit var testName: String
  @Parameter(value = 1) lateinit var exception: Throwable

  @Test
  fun test() {
    throw exception
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun collectBadIcons(ignoreSkipTag: Boolean = false): List<Array<Any>> {
      val checker = MySanityChecker(File(PathManager.getHomePath()), ignoreSkipTag)
      forEachModule {
        checker.check(it)
      }
      return createTestData(checker.failures)
    }

    @JvmStatic
    @JvmOverloads
    fun collectIconsWithNonOptimumSize(iconsOnly: Boolean = true, ignoreSkipTag: Boolean = false): List<Array<Any>> {
      val checker = MyOptimumSizeChecker(File(PathManager.getHomePath()), iconsOnly, ignoreSkipTag)
      forEachModule {
        checker.checkOptimumSizes(it)
      }
      return createTestData(checker.failures)
    }

    private fun createTestData(failures: Collection<FailedTest>): List<Array<Any>> {
      return failures
        .sortedWith(compareBy<FailedTest> { it.module }.thenBy { it.id }.thenBy { it.message })
        .map { arrayOf<Any>(it.getTestName(), it.getException()) }
    }

    private fun forEachModule(action: (JpsModule) -> Unit) {
      val home = PathManager.getHomePath()
      val model = JpsElementFactory.getInstance().createModel()

      val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
      JpsProjectLoader.loadProject(model.project, pathVariables, home)

      model.project.modules.forEach(action)
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

class FailedTest(val module: String, val message: String, val id: String, val paths: List<String>) {
  internal constructor(module: JpsModule, message: String, image: ImagePaths, file: File) :
    this(module.name, message, image.id, listOf(file.absolutePath))

  internal constructor(module: JpsModule, message: String, image: ImagePaths) :
    this(module.name, message, image.id, image.files.values.map { it.absolutePath }.toList())

  fun getTestName(): String = "'${module}' - $id - $message"
  fun getException(): Throwable = Exception("${message}\n\n${paths.joinToString("\n")}")
}