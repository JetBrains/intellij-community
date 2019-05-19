// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.Key;

/**
 * @author yole
 */
public interface TestModeFlagListener {
  void testModeFlagChanged(Key<?> key, Object value);
}
