// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @deprecated Use {@link JavaModuleTestCase} for Java-dependent tests,
 * {@link BasePlatformTestCase} for non-Java-dependent tess
 */
@Deprecated
public abstract class ModuleTestCase extends JavaModuleTestCase {
}
