// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.JavaTestUtil;

public class JavaParsingTestConfigurator extends JavaParsingTestConfiguratorBase {
  public JavaParsingTestConfigurator() {
    super(JavaTestUtil.getMaxRegisteredLanguageLevel());
  }

  @Override
  public boolean checkPsi() {
    return true;
  }
}
