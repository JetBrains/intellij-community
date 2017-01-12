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

class CommunityImageResourcesTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any>> {
      return ImageResourcesTestBase.collectBadIcons(false)
    }
  }
}

@Ignore
class AllImageResourcesTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any>> {
      return ImageResourcesTestBase.collectBadIcons(true)
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
    fun collectBadIcons(ignoreSkipTag: Boolean): List<Array<Any>> {
      val home = PathManager.getHomePath()
      val model = JpsElementFactory.getInstance().createModel()

      val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
      JpsProjectLoader.loadProject(model.project, pathVariables, home)

      val modules = model.project.modules

      val checker = MyChecker(File(home), ignoreSkipTag)
      modules.forEach {
        checker.check(it)
      }
      return checker.collectFailures()
        .sortedWith(compareBy<FailedTest> { it.module }.thenBy { it.id }.thenBy { it.message })
        .map { arrayOf<Any>(it.getTestName(), it.getException()) }
    }
  }
}

private class MyChecker(projectHome: File, ignoreSkipTag: Boolean) : ImageSanityCheckerBase(projectHome, ignoreSkipTag) {
  private val failures = ArrayList<FailedTest>()

  override fun log(severity: ImageSanityCheckerBase.Severity,
                   message: String,
                   module: JpsModule,
                   images: Collection<Pair<String, File>>) {
    if (severity == Severity.INFO) return
    images.forEach { image ->
      failures.add(FailedTest(module.name, message, image.first, image.second.path))
    }
  }

  fun collectFailures(): Collection<FailedTest> {
    return failures
  }
}

class FailedTest(val module: String, val message: String, val id: String, val path: String) {
  fun getTestName(): String = "'${module}' - $id - $message"
  fun getException(): Throwable = Exception("${message} - ${path}")
}