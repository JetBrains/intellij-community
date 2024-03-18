// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.STATIC;

public abstract class AbstractJavaRearrangerTest extends AbstractRearrangerTest {
  @Override
  protected void setUp() throws Exception {
    fileType = JavaFileType.INSTANCE;
    language = JavaLanguage.INSTANCE;
    super.setUp();
  }

  protected List<?> classic = List.of(
    AbstractRearrangerTest.rule(INTERFACE), AbstractRearrangerTest.rule(CLASS), AbstractRearrangerTest.rule(FIELD, STATIC),
    AbstractRearrangerTest.rule(FIELD, PUBLIC), AbstractRearrangerTest.rule(FIELD),
    AbstractRearrangerTest.rule(METHOD, PUBLIC), AbstractRearrangerTest.rule(METHOD));
}
