// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @deprecated Use {@link JavaCompletionTestCase} for Java-dependent completion tests and
 * {@link BasePlatformTestCase} for non-Java-dependent
 * completion tests
 */
@Deprecated
public abstract class CompletionTestCase extends JavaCompletionTestCase {
}
