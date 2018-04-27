// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation;

import com.intellij.openapi.util.registry.Registry;

public class JvmRunLineMarkerTest extends RunLineMarkerTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("ide.jvm.run.marker").setValue(true, getTestRootDisposable());
  }
}
