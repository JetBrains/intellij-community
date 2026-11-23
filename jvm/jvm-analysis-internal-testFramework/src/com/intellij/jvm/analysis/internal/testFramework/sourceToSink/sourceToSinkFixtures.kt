// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SourceToSinkFixtures")

package com.intellij.jvm.analysis.internal.testFramework.sourceToSink

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

/**
 * The fixture of a taint inspection test. It is the JUnit 5 form of [TaintedTestBase]. Create it with [taintedFixture].
 */
@TestOnly
class TaintedFixture<T : InspectionProfileEntry> internal constructor(
  base: JvmInspectionFixture<T>,
) : JvmInspectionFixture<T>(base.fixture, base.inspection) {
  /**
   * Adds the `Tainted` and `Untainted` annotations of the Checker Framework.
   */
  fun prepareCheckFramework() {
    addClass("""
                               package org.checkerframework.checker.tainting.qual;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
                               public @interface Tainted {
                               }
                               """.trimIndent())
    addClass("""
                               package org.checkerframework.checker.tainting.qual;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
                               public @interface Untainted {
                               }
                               """.trimIndent())
  }

  /**
   * Adds the annotations `javax.annotation.Tainted` and `javax.annotation.Untainted`.
   */
  fun prepareJsr() {
    addClass("""
                               package javax.annotation;
                               public @interface Tainted {
                               }
                               """.trimIndent())
    addClass("""
                               package javax.annotation;
                               public @interface Untainted {
                               }
                               """.trimIndent())
  }
}

/**
 * Creates a [TaintedFixture] for one test. [inspection] creates the inspection of the test.
 */
@TestOnly
@JvmOverloads
fun <T : InspectionProfileEntry> taintedFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  inspection: () -> T,
): TestFixture<TaintedFixture<T>> = testFixture("taintedFixture") {
  initialized(TaintedFixture(jvmInspectionFixture(projectFixture, testDataPath, inspection = inspection).init())) {}
}

/**
 * Creates the project of a [SourceToSinkFlowInspection] test class with [LightJavaCodeInsightFixtureTestCase.JAVA_19],
 * as [SourceToSinkFlowInspectionTestBase] does.
 */
@TestOnly
fun sourceToSinkFlowProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_19)

/**
 * Creates the fixture of a [SourceToSinkFlowInspection] test. It replaces [SourceToSinkFlowInspectionTestBase].
 * The inspection warns about complex cases, and it knows the `javax.annotation.Tainted` and `javax.annotation.Untainted` annotations.
 * [configure] changes the inspection before the fixture enables it.
 * Give it the project of [sourceToSinkFlowProjectFixture].
 */
@TestOnly
@JvmOverloads
fun sourceToSinkFlowInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  configure: SourceToSinkFlowInspection.() -> Unit = {},
): TestFixture<TaintedFixture<SourceToSinkFlowInspection>> = taintedFixture(projectFixture, testDataPath) {
  SourceToSinkFlowInspection().also {
    it.warnIfComplex = true
    it.taintedAnnotations.add("javax.annotation.Tainted")
    it.untaintedAnnotations.add("javax.annotation.Untainted")
    it.configure()
  }
}
