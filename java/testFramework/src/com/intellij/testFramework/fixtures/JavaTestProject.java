// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import org.jetbrains.annotations.TestOnly;

/**
 * A Java project with one module that the tests of one {@link com.intellij.testFramework.junit5.AnalysisHostTest} class share.
 * Create it with {@link JavaTestFixtures#javaTestProjectFixture}.
 * <p>
 * Each {@link JavaHostFixtures} has its own implementation. The IDE implementation is {@link LightJavaProject}.
 */
@TestOnly
public interface JavaTestProject {
}
