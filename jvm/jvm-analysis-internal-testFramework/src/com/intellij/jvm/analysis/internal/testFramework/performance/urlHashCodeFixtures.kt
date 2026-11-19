// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UrlHashCodeFixtures")

package com.intellij.jvm.analysis.internal.testFramework.performance

import com.intellij.codeInspection.performance.UrlHashCodeInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.annotations.TestOnly

/**
 * Creates the fixture of an [UrlHashCodeInspection] test. It replaces [UrlHashCodeInspectionTestBase].
 * Give it the project of [com.intellij.jvm.analysis.testFramework.jvmProjectFixture].
 */
@TestOnly
fun urlHashCodeInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<UrlHashCodeInspection>> =
  jvmInspectionFixture(projectFixture) { UrlHashCodeInspection() }
