// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser;

import com.intellij.testFramework.junit5.TestApplication;

@TestApplication
public class JavadocParsingTest extends AbstractBasicJavadocParsingTest {
  public JavadocParsingTest() {
    super(new JavaParsingTestConfigurator());
  }
}