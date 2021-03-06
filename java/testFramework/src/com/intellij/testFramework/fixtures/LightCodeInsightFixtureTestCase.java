// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link LightJavaCodeInsightFixtureTestCase} for Java-dependent tests,
 * {@link BasePlatformTestCase} for non-Java dependent tests
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public abstract class LightCodeInsightFixtureTestCase extends LightJavaCodeInsightFixtureTestCase {
}
