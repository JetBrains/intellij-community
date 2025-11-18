// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.OldJavaParsingTestConfigurator;

// used only to check the old parser
// new features are not supported
@Deprecated
public class OldClassParsingTest extends AbstractBasicClassParsingTest {
  public OldClassParsingTest() {
    super(new OldJavaParsingTestConfigurator("java.FILE"));
  }
}
