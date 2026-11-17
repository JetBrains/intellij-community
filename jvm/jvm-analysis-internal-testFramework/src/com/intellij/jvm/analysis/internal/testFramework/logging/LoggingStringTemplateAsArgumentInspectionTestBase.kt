package com.intellij.jvm.analysis.internal.testFramework.logging

/**
 * @deprecated Use [loggingInspectionFixture] with a `LoggingStringTemplateAsArgumentInspection` in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use loggingInspectionFixture in a JUnit 5 test")
abstract class LoggingStringTemplateAsArgumentInspectionTestBase : LoggingInspectionTestBase()