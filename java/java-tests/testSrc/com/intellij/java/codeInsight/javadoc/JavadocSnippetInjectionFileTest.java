// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.EqualsToFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JavadocSnippetInjectionFileTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadoc/snippet/file";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_20);
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = quickFixFixture.get();
    quickFix.configure(fileName);
    JavaCodeInsightTestFixture fixture = quickFix.getFixture();
    ReadAction.runBlocking(() -> {
      final int offset = fixture.getEditor().getCaretModel().getPrimaryCaret().getOffset();
      final PsiElement snippet = PsiUtilCore.getElementAtOffset(fixture.getFile(), offset);

      final PsiClass injectedClass = getInjectedClass(snippet);
      if (injectedClass == null)  {
        // There should be no "after" file since there is no state
        Assertions.assertFalse(new File(TEST_DATA_PATH, "after" + fileName).exists());
      } else {
      EqualsToFile.assertEqualsToFile(
        "Injected code",
        new File(TEST_DATA_PATH, "after" + fileName),
        injectedClass.getText()
      );
      }
    });
  }

  /// @return the injected class, or null if the injection is disabled
  private @Nullable PsiClass getInjectedClass(PsiElement element) {
    final PsiSnippetDocTag snippet = PsiTreeUtil.getParentOfType(element, PsiSnippetDocTag.class);
    final AtomicReference<PsiElement> injected = new AtomicReference<>();
    final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(quickFixFixture.get().getFixture().getProject());
    injectionManager.enumerate(snippet, (injectedPsi, places) -> { injected.set(injectedPsi); });

    return PsiTreeUtil.findChildOfType(injected.get(), PsiClass.class);
  }
}
