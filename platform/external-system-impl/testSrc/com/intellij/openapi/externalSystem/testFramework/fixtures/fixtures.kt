// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.testFramework.fixtures

import com.intellij.openapi.externalSystem.testFramework.fixtures.impl.MultiProjectTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import java.nio.file.Path

fun multiProjectFixture(
  pathFixture: TestFixture<Path> = tempPathFixture(),
): TestFixture<MultiProjectTestFixture> = testFixture {
  val testRoot = pathFixture.init()
  val fixture = MultiProjectTestFixtureImpl(testRoot)
  initialized(fixture) {}
}