// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.CLASS;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.INTERFACE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.STATIC;

public abstract class AbstractJavaRearrangerTest extends AbstractRearrangerTest {
  protected List<?> classic = List.of(rule(INTERFACE), rule(CLASS), 
                                      rule(FIELD, STATIC), rule(FIELD, PUBLIC), rule(FIELD),
                                      rule(METHOD, PUBLIC), rule(METHOD));

  @Override
  protected void setUp() throws Exception {
    fileType = JavaFileType.INSTANCE;
    language = JavaLanguage.INSTANCE;
    super.setUp();
  }

  protected void doTest(@NotNull @Language("JAVA") String text, @NotNull List<?> rules) {
    doTest(text, text, rules);
  }

  @Override
  protected void doTest(@NotNull @Language("JAVA") String initial, @NotNull @Language("JAVA") String expected, @NotNull List<?> rules) {
    super.doTest(initial, expected, rules);
  }

  @Override
  protected void doTest(@NotNull @Language("JAVA")String initial,
                        @NotNull @Language("JAVA")String expected,
                        @NotNull List<?> rules,
                        @NotNull List<ArrangementGroupingRule> groups) {
    super.doTest(initial, expected, rules, groups);
  }
}
