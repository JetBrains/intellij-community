// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.RearrangerTestUtil;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.psi.codeStyle.arrangement.RearrangerTestUtil.rule;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.CLASS;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.INTERFACE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.STATIC;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class AbstractJavaRearrangerTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  protected List<?> classic = List.of(rule(INTERFACE), rule(CLASS), 
                                      rule(FIELD, STATIC), rule(FIELD, PUBLIC), rule(FIELD),
                                      rule(METHOD, PUBLIC), rule(METHOD));

  protected @NotNull Project getProject() {
    return projectFixture.get();
  }

  protected @NotNull CommonCodeStyleSettings getCommonSettings() {
    return RearrangerTestUtil.getCommonSettings(getProject(), JavaLanguage.INSTANCE);
  }

  protected void doTest(@NotNull @Language("JAVA") String text, @NotNull List<?> rules) {
    doTest(text, text, rules);
  }

  protected void doTest(@NotNull @Language("JAVA") String initial, @NotNull @Language("JAVA") String expected, @NotNull List<?> rules) {
    RearrangerTestUtil.doTest(codeInsightFixture.get(), JavaFileType.INSTANCE, JavaLanguage.INSTANCE, initial, expected, rules);
  }

  protected void doTest(@NotNull @Language("JAVA")String initial,
                        @NotNull @Language("JAVA")String expected,
                        @NotNull List<?> rules,
                        @NotNull List<ArrangementGroupingRule> groups) {
    RearrangerTestUtil.doTest(codeInsightFixture.get(), JavaFileType.INSTANCE, JavaLanguage.INSTANCE, initial, expected, rules, groups);
  }
}
