// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixture;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixtureKt.javaCompletionAutoPopupFixture;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * For tests checking platform behavior not related to Java language (but they may still use Java for code samples)
 */
@NeedsIndex.SmartMode(reason = "AutoPopup shouldn't work in dumb mode")
@TestApplication
public class GeneralAutoPopupTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<JavaCompletionAutoPopupFixture> autoPopupFixture = javaCompletionAutoPopupFixture(projectFixture);
  private LightJavaCodeInsightTestFixture myFixture;
  private CompletionAutoPopupTester myTester;

  @BeforeEach
  void setUp() {
    myFixture = autoPopupFixture.get().getFixture();
    myTester = autoPopupFixture.get().getTester();
  }

  private void type(String s) {
    autoPopupFixture.get().type(s);
  }

  private LookupImpl getLookup() {
    return autoPopupFixture.get().getLookup();
  }

  @Test
  public void testNoAutopopupInTheMiddleOfWordWhenTheOnlyVariantIsAlreadyInTheEditor() {
    myFixture.configureByText("a.java", "class Foo { private boolean ignoredProperty; public boolean isIgnoredP<caret>operty() {}}");
    type("r");
    assertNull(getLookup());
  }

  @Test
  public void testNoLookupAfterTypingALetterAndThenQuicklyOvertypingAQuote() {
    myFixture.configureByText("a.html", "<a href=\"<caret>\">");
    myFixture.type("a");
    type("\"");
    assertNull(getLookup());
  }

  @Test
  public void testNoLookupAfterTypingAndQuicklyMovingCaretToAnotherPlace() throws Throwable {
    myFixture.configureByText("a.java", "class Foo { <caret> }");
    runInEdtAndWait((ThrowableRunnable<Throwable>)() -> {
      myFixture.type("F");
      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset() + 1);
    });

    myTester.joinAutopopup();
    myTester.joinCompletion();

    assertNull(getLookup());
  }

  @Test
  public void testInjectorsAreNotRunInEdt() {
    final Ref<Boolean> injectorCalled = new Ref<>(false);
    LanguageInjector injector = (host, injectionPlacesRegistrar) -> {
      injectorCalled.set(true);
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    };
    ExtensionTestUtil.maskExtensions(LanguageInjector.EXTENSION_POINT_NAME,
                                     Arrays.asList(injector),
                                     myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", "class Foo { String s = <caret>; }");
    assertFalse(injectorCalled.get());
    type("\"");

    injectorCalled.set(false);
    type("abc");

    assertTrue(injectorCalled.get());
    assertNull(getLookup());
  }

  @Test
  public void testDonTCloseLookupWhenStartingANewLineAfterDot() throws Throwable {
    myFixture.configureByText("a.java", "class Foo {{ \"abc\"<caret> }}");
    type(".");
    assertNotNull(getLookup());
    runInEdtAndWait((ThrowableRunnable<Throwable>)() -> {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE);
      assertNotNull(getLookup());
      assertEquals(getLookup().getLookupStart(), myFixture.getEditor().getCaretModel().getOffset());
      assertTrue(myFixture.getEditor().getDocument().getText().contains("\n"));
    });
  }

  @Test
  public void testCloseLookupWhenStartingANewLineAfterHavingTypedAnIdentifierManually() throws Throwable {
    myFixture.configureByText("a.java", "class Foo { int a, ab; { new Foo()<caret> }}");
    type(".a");
    assertNotNull(getLookup());
    runInEdtAndWait((ThrowableRunnable<Throwable>)() -> {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE);
      assertNull(getLookup());
      assertTrue(myFixture.getEditor().getDocument().getText().contains("\n"));
    });
  }

  @Test
  public void testDoNotRestartAutopopupCompletionWhenAppendingPrefixWhichAlreadyYieldedNoResultsToSaveCpu() {
    JavaAutoPopupTest.registerCompletionContributor(CountingContributor.class, myFixture.getTestRootDisposable(), LoadingOrder.FIRST);
    CountingContributor.getOurCount().set(0);

    myFixture.configureByText("a.txt", "");
    type("a");
    assertNull(getLookup());
    assertEquals(1, CountingContributor.getOurCount().get());

    type("bcd");
    assertNull(getLookup());
    assertEquals(1, CountingContributor.getOurCount().get());

    type(" a");
    assertEquals(2, CountingContributor.getOurCount().get());
  }

  @Test
  public void testSkipAutopopupIfConfidenceNeedsNonReadyIndex() {
    myFixture.configureByText("a.java", "class C { int abc; { getClass().getDeclaredField(\"<caret>x\"); }}");
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
      type("a");
      if (Registry.is("ide.dumb.mode.check.awareness")) {
        assertNull(getLookup());
      }
      else {
        myFixture.assertPreferredCompletionItems(0, "abc");
      }
    });
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "abc");
  }

  @Test
  @NeedsIndex.Full
  public void testDotAutoPopupInCodeFragment() {
    PsiClass psiClass = myFixture.addClass("package foo; public final class Foo { public static final int ANSWER = 42; }");
    PsiClassType psiType = ReadAction.compute(() -> PsiTypesUtil.getClassType(psiClass));
    PsiExpressionCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(myFixture.getProject()).createExpressionCodeFragment("Foo<caret>", null, psiType, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    type(".");
    assertNotNull(getLookup());
  }

  public static class CountingContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      ourCount.incrementAndGet();
    }

    public static AtomicInteger getOurCount() {
      return ourCount;
    }

    private static final AtomicInteger ourCount = new AtomicInteger();
  }
}
