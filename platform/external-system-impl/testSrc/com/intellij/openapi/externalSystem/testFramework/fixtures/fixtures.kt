// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.testFramework.fixtures

import com.intellij.openapi.externalSystem.testFramework.fixtures.impl.MultiProjectTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

fun multiProjectFixture(): TestFixture<MultiProjectTestFixture> = testFixture {
  initialized(MultiProjectTestFixtureImpl()) {}
}