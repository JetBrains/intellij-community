// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

/**
 * @deprecated Use {@link LightJavaCodeInsightTestCase} for Java-dependent tests,
 * {@link LightPlatformCodeInsightTestCase} for non-Java-dependent tests
 */
@Deprecated
public abstract class LightCodeInsightTestCase extends LightJavaCodeInsightTestCase {
}
