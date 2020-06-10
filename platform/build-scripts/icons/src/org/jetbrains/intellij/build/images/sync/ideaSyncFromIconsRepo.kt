// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.intellij.build.images.generateIconsClasses
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) = try {
  if (args.isEmpty()) System.err.println("If you haven't intended to start full icons sync" +
                                         " then please specify required icons repo's commit hashes" +
                                         " joined by comma, semicolon or space in arguments")
  System.setProperty(Context.iconsCommitHashesToSyncArg, args.joinToString())
  echo("Syncing icons..")
  checkIcons()
  echo("Generating classes..")
  generateIconsClasses()
  // TODO: perform compilation
  echo("Running tests..")
  val tests = mapOf(
    "intellij.platform.tests" to listOf("com.intellij.ui.PlatformIconsAPITest"),
    "intellij.idea.ultimate.tests.main" to listOf("com.intellij.openapi.icons.UltimateIconClassesTest",
                                                  "com.intellij.openapi.icons.UltimateImageResourcesSanityTest"),
    "intellij.platform.images.build" to listOf("org.jetbrains.intellij.build.images.CommunityIconClassesTest",
                                               "org.jetbrains.intellij.build.images.CommunityImageResourcesSanityTest")
  )
  runTests(tests = tests.values.flatten(), modules = tests.keys)
}
finally {
  // in case of non-daemon threads spawned from tests
  exitProcess(0)
}

private fun echo(msg: String) = println("\n** $msg")

private fun runTests(tests: Collection<String>, modules: Collection<String>) {
  val project = System.getProperty("user.dir")
  val out = "$project/out"
  val classpath = listOf("$out/classes/production", "$out/classes/test", "$project/lib")
    .flatMap { File(it).listFiles()?.toList() ?: error(it) }
    .filter { it.isDirectory || it.extension == "jar" }
    .plus(modules.flatMap { dependencies(project, it) })
    .map { it.toURI().toURL() }
  val testClassLoader = UrlClassLoader.build().urls(classpath).get()
  val testRunner = testClassLoader
    .loadClass("org.jetbrains.intellij.build.images.sync.IdeaTestRunnerKt")
    .getDeclaredMethod("runTest", Class::class.java)
  tests.forEach {
    testRunner.invoke(null, testClassLoader.loadClass(it))
  }
}

private fun dependencies(projectPath: String, module: String) =
  jpsProject(projectPath)
    .modules.first { it.name == module }
    .let(JpsJavaExtensionService::dependencies)
    .recursively().libraries
    .flatMap { it.getRoots(JpsOrderRootType.COMPILED) }
    .map { expandJpsMacro(it.url) }
    .map(JpsPathUtil::urlToFile)