// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.junit5.AnalysisHostRuntime
import com.intellij.testFramework.junit5.AnalysisHostTest
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.siyeh.ig.JavaInspectionFixture
import org.jetbrains.annotations.TestOnly
import java.util.ServiceLoader
import java.util.function.Function

/**
 * Creates the Java fixtures of a [AnalysisHostTest] class on one [AnalysisHostTest.Host].
 *
 * The current implementation is the provider that [ServiceLoader] finds for the host of [AnalysisHostRuntime.current].
 * The IDE implementation needs no provider.
 */
@TestOnly
interface JavaHostFixtures {
  val host: AnalysisHostTest.Host

  /**
   * Creates the project that the tests of one class share, with the configuration of [descriptor].
   * A host shares the data of equal descriptors, as the IDE shares a light project.
   * A host that does not support [descriptor] aborts the tests of the class.
   */
  fun projectFixture(descriptor: LightProjectDescriptor): TestFixture<@JvmWildcard JavaTestProject>

  /**
   * Creates the inspection fixture of one test in [project].
   */
  fun inspectionFixture(project: TestFixture<JavaTestProject>, spec: InspectionSpec): TestFixture<@JvmWildcard JavaInspectionFixture>

  /**
   * The parameters of an inspection fixture.
   *
   * @param inspection         creates the inspection from the name of the test method without the `test` prefix.
   *                           It can return `null` for a test that enables its inspections itself.
   * @param environmentClasses the classes that the fixture adds before the test
   * @param basePath           the test data path relative to the community root, or `null` for the path of the inspection class
   * @param inspectionClass    the inspection class for the file-based helpers, or `null` for the class of the inspection
   * @param testDataPath       an absolute test data path. When it is set, the fixture ignores [basePath].
   */
  @JvmRecord
  data class InspectionSpec(
    val inspection: Function<String, out InspectionProfileEntry?>,
    val environmentClasses: List<String>,
    val basePath: String?,
    val inspectionClass: Class<out InspectionProfileEntry>?,
    val testDataPath: String?,
  )

  companion object {
    private val current: JavaHostFixtures by lazy {
      val host = AnalysisHostRuntime.current().host
      ServiceLoader.load(JavaHostFixtures::class.java, JavaHostFixtures::class.java.classLoader).firstOrNull { it.host == host }
      ?: run {
        check(host == AnalysisHostTest.Host.IDE) { "No JavaHostFixtures for $host is on the classpath of the test" }
        IdeJavaHostFixtures()
      }
    }

    /**
     * Returns the implementation for the current host.
     *
     * @throws IllegalStateException when the classpath has no implementation for a host other than the IDE
     */
    @JvmStatic
    fun current(): JavaHostFixtures = current
  }
}
