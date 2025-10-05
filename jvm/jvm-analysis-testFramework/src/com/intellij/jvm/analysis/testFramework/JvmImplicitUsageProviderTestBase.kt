package com.intellij.jvm.analysis.testFramework

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection

/**
 * A test base for testing [com.intellij.codeInsight.daemon.ImplicitUsageProvider] implementations in all JVM languages.
 */
abstract class JvmImplicitUsageProviderTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry by lazy { UnusedDeclarationInspection(true) }
}