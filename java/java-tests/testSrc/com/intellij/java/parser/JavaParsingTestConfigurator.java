// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import org.jetbrains.annotations.NotNull;

public class JavaParsingTestConfigurator extends JavaParsingTestConfiguratorBase {
  public JavaParsingTestConfigurator() {
    super(JavaTestUtil.getMaxRegisteredLanguageLevel());
  }

  @Override
  public void setUp(@NotNull AbstractBasicJavaParsingTestCase testCase) {
    super.setUp(testCase);

    testCase.getProject().registerService(LanguageLevelProjectExtension.class,
                                          new LanguageLevelProjectExtensionImpl(testCase.getProject()));
  }

  @Override
  public boolean checkPsi() {
    return true;
  }
}
