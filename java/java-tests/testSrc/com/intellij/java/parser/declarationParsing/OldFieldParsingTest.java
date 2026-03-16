// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.OldJavaParsingTestConfigurator;

// used only to check the old parser
// new features are not supported
@Deprecated
public class OldFieldParsingTest extends AbstractBasicFieldParsingTest {
  public OldFieldParsingTest() {
    super(new OldJavaParsingTestConfigurator("java.FILE"));
  }
}