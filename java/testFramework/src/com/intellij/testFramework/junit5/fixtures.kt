// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@TestOnly
fun javaCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<JavaCodeInsightTestFixture> = codeInsightFixture(projectFixture, tempDirFixture, ::JavaCodeInsightTestFixtureImpl)