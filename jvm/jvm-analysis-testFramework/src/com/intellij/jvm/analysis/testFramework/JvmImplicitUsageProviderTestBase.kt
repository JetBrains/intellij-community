package com.intellij.jvm.analysis.testFramework

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection

/**
 * A test base for testing [com.intellij.codeInsight.daemon.ImplicitUsageProvider] implementations in all JVM languages.
 *
 * @deprecated Use [jvmImplicitUsageProviderFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use jvmImplicitUsageProviderFixture in a JUnit 5 test")
abstract class JvmImplicitUsageProviderTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry by lazy { UnusedDeclarationInspection(true) }
}