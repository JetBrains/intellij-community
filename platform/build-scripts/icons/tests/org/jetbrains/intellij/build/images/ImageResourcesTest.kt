// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtil
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
import java.nio.file.Path
import java.nio.file.Paths

class CommunityImageResourcesSanityTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return collectBadIcons(TestRoot.COMMUNITY)
    }
  }
}

class CommunityImageResourcesOptimumSizeTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return collectIconsWithNonOptimumSize(TestRoot.COMMUNITY)
    }
  }
}

class CommunityIconClassesTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return collectNonRegeneratedIconClasses(TestRoot.COMMUNITY)
    }
  }
}

@Ignore
class AllImageResourcesSanityTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return collectBadIcons(TestRoot.ALL, true)
    }
  }
}

@Ignore
class AllImageResourcesOptimumSizeTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return collectIconsWithNonOptimumSize(TestRoot.ALL, false, true)
    }
  }
}

@Ignore
class AllIconClassesTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return collectNonRegeneratedIconClasses(TestRoot.ALL)
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
      val checker = MySanityChecker(Paths.get(PathManager.getHomePath()), ignoreSkipTag)
      val config = IntellijIconClassGeneratorConfig()
      modules.forEach {
        checker.check(it, config.getConfigForModule(it.name))
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
      val checker = MyOptimumSizeChecker(Path.of(PathManager.getHomePath()), iconsOnly, ignoreSkipTag)
      modules.forEach {
        checker.checkOptimumSizes(it)
      }
      return createTestData(modules, checker.failures)
    }

    @JvmStatic
    fun collectNonRegeneratedIconClasses(root: TestRoot): List<Array<Any?>> {
      val model = loadProjectModel(root)
      val modules = collectModules(root, model)
      val checker = MyIconClassFileChecker(Path.of(PathManager.getHomePath()), model.project.modules)
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

    private fun collectModules(root: TestRoot, model: JpsModel): List<JpsModule> {
      val home = getHomePath(root)
      return model.project.modules
        .filter {
          !(root == TestRoot.ULTIMATE && isCommunityModule(home, it)) && !it.name.startsWith("fleet.") && it.name != "fleet"
        }
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

private class MySanityChecker(projectHome: Path, ignoreSkipTag: Boolean) : ImageSanityCheckerBase(projectHome, ignoreSkipTag) {
  val failures: MutableList<FailedTest> = ArrayList()

  override fun log(severity: Severity,
                   message: String,
                   module: JpsModule,
                   images: Collection<ImageInfo>) {
    if (severity == Severity.INFO) return
    images.forEach { image ->
      failures.add(FailedTest(module, message, image))
    }
  }
}

private class MyOptimumSizeChecker(val projectHome: Path, val iconsOnly: Boolean, val ignoreSkipTag: Boolean) {
  val failures = ContainerUtil.createConcurrentList<FailedTest>()

  private val config = IntellijIconClassGeneratorConfig()

  fun checkOptimumSizes(module: JpsModule) {
    val allImages = ImageCollector(projectHome, iconsOnly, ignoreSkipTag, moduleConfig = config.getConfigForModule(module.name)).collect(module)
    allImages.parallelStream().filter { it.basicFile != null }.forEach { image ->
      image.files.parallelStream().forEach { file ->
        val optimized = ImageSizeOptimizer.optimizeImage(file)
        if (optimized != null && !optimized.hasOptimumSize) {
          failures.add(FailedTest(module, "image size can be optimized using " +
                                          "\"Icons processing | Generate icon classes\" run configuration: " +
                                          optimized.compressionStats, image, file.toFile()))
        }
      }
    }
  }
}

private class MyIconClassFileChecker(private val projectHome: Path, private val modules: List<JpsModule>) {
  val failures = ArrayList<FailedTest>()

  private val config = IntellijIconClassGeneratorConfig()

  /**
   * See [org.jetbrains.intellij.build.images.RobotFileHandler] for supported icon-robots.txt rules.
   */
  fun checkIconClasses(module: JpsModule) {
    val generator = IconsClassGenerator(projectHome, modules, false)
    generator.processModule(module, config.getConfigForModule(module.name))

    generator.getModifiedClasses().forEach { (module, file, details) ->
      failures.add(FailedTest(module, "icon class file should be regenerated using " +
                                      "\"Icons processing | Generate icon classes\" run configuration, " +
                                      "or new icons be ignored via 'icon-robots.txt'", file, details))
    }
  }
}

class FailedTest internal constructor(val module: String, val message: String, val id: String, private val details: String) {
  internal constructor(module: JpsModule, message: String, image: ImageInfo, file: File) :
    this(module.name, message, image.id, file.absolutePath)

  internal constructor(module: JpsModule, message: String, image: ImageInfo) :
    this(module.name, message, image.id, image.files.joinToString("\n") { it.toAbsolutePath().toString() })

  internal constructor(module: JpsModule, message: String, file: Path, details: CharSequence) :
    this(module.name, message, file.fileName.toString(), "$file\n\n$details")

  fun getTestName(): String = "'${module}' - $id - ${message.substringBefore('\n')}"
  fun getException(): Throwable = Exception("${message}\n\n$details".trim())
}