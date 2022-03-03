// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.TestingTasks
import org.jetbrains.intellij.build.impl.CompilationContextImpl

/**
 * Compiles the sources and runs tests from 'community' project. Look at [org.jetbrains.intellij.build.TestingOptions] to see which
 * options are supported.
 *
 * If you want to run this script from IntelliJ IDEA, it's important to add 'Build Project' step in 'Before Launch' section of the created
 * run configuration to ensure that required files are compiled before the script starts. It also makes sense to have
 * [org.jetbrains.intellij.build.BuildOptions.USE_COMPILED_CLASSES_PROPERTY] '-Dintellij.build.use.compiled.classes=true' in 'VM Options'
 * to skip compilation and use the compiled classes from the project output.
 */
object CommunityRunTestsBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    val outputDir = "$communityHome/out/tests"
    val context = CompilationContextImpl.create(communityHome.toString(), communityHome.toString(), outputDir)
    TestingTasks.create(context).runTests(emptyList(), "intellij.idea.community.main", null)
  }
}