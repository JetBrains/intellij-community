// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JavadocSnippetInjectionTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadoc/snippet";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_20);
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    ActionHint hint = quickFixFixture.get().configure(fileName);
    final Language language = ReadAction.computeBlocking(() -> getInjectedLanguage());
    final Language expectedLang = Language.findLanguageByID(hint.getExpectedText());

    if (language == null) {
      assertThat(expectedLang)
      .withFailMessage("no language found but one language expected: ", expectedLang.getID())
      .isEqualTo(Language.ANY);
    } else {
    assertThat(language)
      .withFailMessage(String.format("Language '%s' should be injected, but found '%s'", hint.getExpectedText(), language.getID()))
      .isEqualTo(expectedLang);
    }
  }

  private @Nullable Language getInjectedLanguage() {
    JavaCodeInsightTestFixture fixture = quickFixFixture.get().getFixture();
    final int offset = fixture.getEditor().getCaretModel().getPrimaryCaret().getOffset();
    final PsiElement element = PsiUtilCore.getElementAtOffset(fixture.getFile(), offset);
    final PsiSnippetDocTag snippet = PsiTreeUtil.getParentOfType(element, PsiSnippetDocTag.class);
    final AtomicReference<PsiElement> injected = new AtomicReference<>();
    final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(fixture.getProject());
    injectionManager.enumerate(snippet, (injectedPsi, places) -> { injected.set(injectedPsi); });
    
    // For tests with no language injected
    if (injected.get() == null) {
      return null;
    }
    
    return injected.get().getLanguage();
  }
}
