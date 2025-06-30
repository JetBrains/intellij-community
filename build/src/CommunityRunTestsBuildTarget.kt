// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.TestingOptions
import org.jetbrains.intellij.build.TestingTasks
import org.jetbrains.intellij.build.impl.createCompilationContext

/**
 * Compiles the sources and runs tests from the 'community' project. Look at [org.jetbrains.intellij.build.TestingOptions] to see which
 * options are supported.
 *
 * If you want to run this script from IntelliJ IDEA, it's important to add the 'Build Project' step in the 'Before Launch' section of the created
 * run configuration to ensure that required files are compiled before the script starts. It also makes sense to have
 * [org.jetbrains.intellij.build.BuildOptions.USE_COMPILED_CLASSES_PROPERTY] '-Dintellij.build.use.compiled.classes=true' in 'VM Options'
 * to skip compilation and use the compiled classes from the project output.
 */
object CommunityRunTestsBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val context = createCompilationContext(
        projectHome = COMMUNITY_ROOT.communityRoot,
        defaultOutputRoot = COMMUNITY_ROOT.communityRoot.resolve("out/tests")
      )
      val options = TestingOptions()
      options.mainModule = options.mainModule ?: "intellij.idea.community.main"
      TestingTasks.create(context, options).runTests()
    }
  }
}