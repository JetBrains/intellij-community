// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runners.Suite
import java.io.File
import java.util.jar.JarFile

/**
 * Guards the hand-maintained [AllTests] suite: under Bazel only the classes listed in
 * `@Suite.SuiteClasses` run, so a test class missing from the list would silently never execute.
 */
class AllTestsCompletenessTest {
  @Test
  fun `every class with JUnit tests is registered in AllTests`() {
    val registered = AllTests::class.java.getAnnotation(Suite.SuiteClasses::class.java).value
      .map { suiteClass -> suiteClass.java.name }
      .toSet()

    val jar = File(AllTests::class.java.protectionDomain.codeSource.location.toURI())
    val testClasses = JarFile(jar).use { entries ->
      entries.entries().asSequence()
        .map { entry -> entry.name }
        .filter { name -> name.endsWith(".class") && !name.contains('$') }
        .map { name -> name.removeSuffix(".class").replace('/', '.') }
        .filter { name -> name.startsWith("org.jetbrains.bazel.wasmjs.") }
        .map { name -> Class.forName(name) }
        .filter { type -> type.declaredMethods.any { it.isAnnotationPresent(Test::class.java) } }
        .map { type -> type.name }
        .toSet()
    }

    assertEquals(emptySet<String>(), testClasses - registered)
  }
}
