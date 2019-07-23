// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * NOTE: test was changed to <em>light</em> test in 2019.1 - use dedicated {@link ProjectDescriptor ProjectDescriptor} or
 * rollback project setup changes in {@code tearDown()} to avoid side effects.
 *
 * @deprecated Use {@link JavaInspectionTestCase} for Java-dependent tests,
 * {@link BasePlatformTestCase} for non-Java-dependent tests
 */
@Deprecated
public abstract class InspectionTestCase extends JavaInspectionTestCase {
}
