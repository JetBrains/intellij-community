// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.common.ThreadUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertNotEquals;

@NeedsIndex.SmartMode(reason = "AutoPopup shouldn't work in dumb mode")
public class JavaAutoPopupTest extends JavaCompletionAutoPopupTestCase {
  public void testNewItemsOnLongerPrefix() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>
        }
      }""");
    type("i");
    List<String> les = myFixture.getLookupElementStrings();
    assertTrue(les.contains("iterable"));
    assertTrue(les.contains("if"));
    assertTrue(les.contains("int"));

    type("t");
    assertContains("iterable");
    TestCase.assertEquals("iterable", getLookup().getCurrentItem().getLookupString());

    type("er");
    les = myFixture.getLookupElementStrings();
    assertTrue(les.contains("iterable"));
    assertTrue(les.contains("iter"));
    TestCase.assertEquals("iter", getLookup().getCurrentItem().getLookupString());
    assertTrue(getLookup().isFocused());

    type("a");
    assertTrue(getLookup().isFocused());
  }

  public void testAfterDblColon() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo() {
          Runnable::<caret>
        }
      }""");
    type("r");
    List<String> les = myFixture.getLookupElementStrings();
    assertTrue(les.contains("run"));
    assertTrue(getLookup().isFocused());
  }

  public void assertContains(String... items) {
    assertTrue(myFixture.getLookupElementStrings().containsAll(Arrays.asList(items)));
  }

  public void testRecalculateItemsOnBackspace() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int itaa;
          ite<caret>
        }
      }""");
    type("r");
    myFixture.assertPreferredCompletionItems(0, "iter", "iterable");

    type("\b");
    assertContains("iterable");

    type("\b");
    assertContains("itaa", "iterable");
    type("a");
    assertContains("itaa");
    type("\b");
    assertContains("itaa", "iterable");
    type("e");
    assertContains("iterable");

    type("r");
    myFixture.assertPreferredCompletionItems(0, "iter", "iterable");
  }

  public void testExplicitSelectionShouldSurvive() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int iterable2;
          it<caret>
        }
      }""");
    type("e");
    assertContains("iterable", "iterable2");

    TestCase.assertEquals("iterable", getLookup().getCurrentItem().getLookupString());
    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN));
    assertEquals("iterable2", getLookup().getCurrentItem().getLookupString());

    type("r");
    assertEquals("iter", getLookup().getItems().get(0).getLookupString());
    assertEquals("iterable2", getLookup().getCurrentItem().getLookupString());
  }

  public void testExplicitMouseSelectionShouldSurvive() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int iterable2;
          it<caret>
        }
      }""");
    type("e");
    assertContains("iterable", "iterable2");

    TestCase.assertEquals("iterable", getLookup().getCurrentItem().getLookupString());
    edt(() -> setCurrentItem(getLookup(), getLookup().getItems().get(1)));
    TestCase.assertEquals("iterable2", getLookup().getCurrentItem().getLookupString());

    type("r");
    myFixture.assertPreferredCompletionItems(2, "iter", "iterable", "iterable2");
  }

  public void testGenerallyFocusLookupInJavaMethod() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo(String iterable) {
          return it<caret>;
        }
      }""");
    type("e");
    TestCase.assertTrue(getLookup().isFocused());
  }

  public void testNoStupidNameSuggestions() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo(String <caret>) {
        }
      }""");
    type("x");
    assertNull(myFixture.getLookupElementStrings());
  }

  public void testExplicitSelectionShouldBeHonoredFocused() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo() {
          int abcd;
          int abce;
          a<caret>
        }
      }""");
    type("b");
    assertTrue(getLookup().isFocused());
    type("c");

    assertContains("abcd", "abce");
    TestCase.assertEquals("abcd", getLookup().getCurrentItem().getLookupString());
    edt(() -> setCurrentItem(getLookup(), getLookup().getItems().get(1)));
    TestCase.assertEquals("abce", getLookup().getCurrentItem().getLookupString());

    type("\t");
    myFixture.checkResult("""
                               class Foo {
                                 String foo() {
                                   int abcd;
                                   int abce;
                                   abce<caret>
                                 }
                               }""");
  }

  public void test_popup_in_javadoc_reference() {
    myFixture.configureByText("a.java", """
      /**
       * {@link AIO<caret>}
       */
      class Foo {}""");
    type("O");
    assertNotNull(getLookup());
  }

  public void test_popup_after_hash_in_javadoc() {
    myFixture.configureByText("a.java", """
      /**
       * {@link String<caret>}
       */
      class Foo {}""");
    type("#");
    assertNotNull(getLookup());
  }

  public void test_popup_in_javadoc_local_reference() {
    myFixture.configureByText("a.java", """
      /**
       * {@link #<caret>}
       */
      class Foo {
        void foo() {}
      }""");
    type("f");
    assertNotNull(getLookup());
  }

  public void test_autopopup_in_javadoc_tag_name() {
    //noinspection JavadocDeclaration
    myFixture.configureByText("a.java", """
      /**
       * @a<caret>
       */
      class Foo {}""");
    type("u");
    assertNotNull(getLookup());
  }

  public void test_no_autopopup_in_javadoc_parameter_descriptions() {
    myFixture.configureByText("a.java", """
      class Foo {
        /**
         * @param o some sentence
         */
        void foo(Object o) {}
      
        /**
         * @param o s<caret>
         */
        void foo2(Object o) {}
      }""");
    type("o");
    assertNull(getLookup());
  }

  public void test_autopopup_in_javadoc_parameter_name() {
    //noinspection JavadocDeclaration
    myFixture.configureByText("a.java", """
      class Foo {
        /**
         * @param <caret>
         */
        void foo2(Object oooooooo) {}
      }""");
    type("o");
    assertNotNull(getLookup());
  }

  public void test_no_autopopup_at_the_end_of_line_comment() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class Foo {
        // foo bar <caret>
      }""");
    type("t");
    assertNull(getLookup());
  }

  public void testPrefixLengthDependentSorting() {
    myFixture.addClass("package foo; public class PsiJavaCodeReferenceElement {}");
    myFixture.configureByText("a.java", """
      import foo.PsiJavaCodeReferenceElement

      class PsiJavaCodeReferenceElementImpl
        { <caret>
      """);
    type("PJCR");
    assertContains("PsiJavaCodeReferenceElement", "PsiJavaCodeReferenceElementImpl");
  }

  public void testQuickSelectAfterReuse() {
    myFixture.configureByText("a.java", """
      class A { Iterable<?> iterable;
        { <caret> }
      }""");
    type("ite");
    edt(() -> {
      myFixture.type("r");
      getLookup().markReused();
      getLookup().setCurrentItem(getLookup().getItems().get(0));
      CommandProcessor.getInstance().executeCommand(getProject(), () -> getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR), null, null);
    });
    myFixture.checkResult("""
                           class A { Iterable<?> iterable;
                             { iterable<caret> }
                           }""");
  }

  public void testQuickSelectAfterReuseAndBackspace() {
    myFixture.configureByText("a.java", """
        class A { Iterable<?> iterable;
          { <caret> }
        }""");
    type("ite");
    edt(() -> {
      myFixture.type("r");
      getLookup().markReused();
      myFixture.type("\b\b");
      getLookup().setCurrentItem(getLookup().getItems().get(0));
      CommandProcessor.getInstance()
        .executeCommand(getProject(), () -> getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR), null, null);
    });
    myFixture.checkResult("""
                             class A { Iterable<?> iterable;
                               { iterable<caret> }
                             }""");
  }

  public void testQuickSelectLiveTemplate() {
    myFixture.configureByText("a.java", """
        class A {
          { <caret> }
        }""");
    type("th");
    edt(() -> myFixture.type("r\t"));
    myFixture.checkResult("""
                             class A {
                               { throw new <caret> }
                             }""");
  }

  public void testTwoQuickRestartsAfterHiding() {
    for (int i = 0; i <= 10; i++) {
      myFixture.configureByText("a" + i + ".java", """
          class A {
            { <caret> }
          }""");
      edt(() -> myFixture.type("A"));
      joinAutopopup();// completion started
      final Ref<Boolean> tooQuick = Ref.create(false);
      edt(() -> {
        tooQuick.set(getLookup() == null);
        myFixture.type("IO");
      });
      joinAutopopup();//I
      joinAutopopup();//O
      joinCompletion();
      assertNotNull(getLookup());
      assertTrue(myFixture.getLookupElementStrings().contains("ArrayIndexOutOfBoundsException"));
      if (!tooQuick.get()) {
        return;
      }

      edt(() -> {
        LookupManager.getInstance(getProject()).hideActiveLookup();
        CompletionProgressIndicator.cleanupForNextTest();
      });
    }

    TestCase.fail("too many too quick attempts");
  }

  private void joinSomething(int degree) {
    if (degree == 0) return;
    joinCommit();
    if (degree == 1) return;
    joinCommit();
    if (degree == 2) return;
    edt(() -> {});
    if (degree == 3) return;
    joinCompletion();
  }

  public void joinCompletion() {
    myTester.joinCompletion();
  }

  protected void joinCommit(Runnable c1) {
    myTester.joinCommit(c1);
  }

  protected void joinCommit() {
    joinCommit(() -> {});
  }

  protected void joinAutopopup() {
    myTester.joinAutopopup();
  }

  private static <Value extends LookupElement> Value setCurrentItem(LookupImpl propOwner, Value item) {
    propOwner.setCurrentItem(item);
    return item;
  }

  public void testTypingDuringExplicitCompletion() {
    myFixture.configureByText("a.java", """
          class A {
            static { Runnable r = new <caret> }
          }""");
    myFixture.complete(CompletionType.SMART);
    edt(() -> myFixture.type("Thr"));
    joinCompletion();
    assertNotNull(getLookup());
    assertTrue(myFixture.getLookupElementStrings().contains("Thread"));
  }

  public void testDotAfterVariable() {
    myFixture.configureByText("a.java", """
          class A {
            { Object ooo; <caret> }
          }""");
    type("o.");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("ooo."));
    assertNotNull(getLookup());
  }

  public void testDotAfterCall() {
    myFixture.configureByText("a.java", """
        class A {
          { <caret> }
        }""");
    type("tos.");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("toString()."));
    assertNotNull(getLookup());
  }

  public void testDotAfterClassName() {
    myFixture.configureByText("a.java", """
      class A {
        { <caret> }
      }""");
    type("AIOO.");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("ArrayIndexOutOfBoundsException."));
    assertNotNull(getLookup());
  }

  public void testDotAfterClassNameInParameter() {
    myFixture.configureByText("a.java", """
      class A {
        void foo(<caret>) {}
      }""");
    type("AIOO...");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("ArrayIndexOutOfBoundsException..."));
    assertNull(getLookup());
  }

  public void doTestArrows(final String toType, final LookupFocusDegree focusDegree, int indexDown, int indexUp) {
    BiConsumer<String, Integer> checkArrow = (action, expectedIndex) -> {
      myFixture.configureByText("a.java", """
          class A {
            void foo() {}
            void farObject() {}
            void fzrObject() {}
            { <caret> }
          }""");

      type(toType);
      assertNotNull(getLookup());
      getLookup().setLookupFocusDegree(focusDegree);

      edt(() -> myFixture.performEditorAction(action));
      if (getLookup() != null) {
        assertTrue(getLookup().isFocused());
        assertTrue(expectedIndex >= 0);
        assertEquals(getLookup().getItems().get(expectedIndex), getLookup().getCurrentItem());
        edt(() -> getLookup().hide());
      }
      else {
        assertEquals(-1, (int)expectedIndex);
      }

      type("\b");
    };

    checkArrow.accept(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, indexUp);
    checkArrow.accept(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, indexDown);
  }

  public void test_vertical_arrows_in_non_focused_lookup() {
    String toType = "ArrayIndexOutOfBoundsException ind";
    doTestArrows(toType, LookupFocusDegree.UNFOCUSED, 0, 2);

    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("ide.cycle.scrolling", false, getTestRootDisposable());
    doTestArrows(toType, LookupFocusDegree.UNFOCUSED, 0, -1);
  }

  public void test_vertical_arrows_in_semi_focused_lookup() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    String toType = "fo";
    doTestArrows(toType, LookupFocusDegree.SEMI_FOCUSED, 2, 0);

    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("ide.cycle.scrolling", false, getTestRootDisposable());
    doTestArrows(toType, LookupFocusDegree.SEMI_FOCUSED, 2, 0);
  }

  public void testHideOnOnePrefixVariant() {
    myFixture.configureByText("a.java", """
      class A {
        Object foo() { return nu<caret> }
      }""");
    type("ll");
    assertNull(getLookup());
  }

  public void testResumeAfterBackspace() {
    myFixture.configureByText("a.java", """
      class A {
        Object foo() { this<caret> }
      }""");
    type(".");
    assertNotNull(getLookup());
    type("x");
    assertNull(getLookup());
    type("\b");
    assertNull(getLookup());
    type("c");
    assertNotNull(getLookup());
  }

  public void testHideOnInvalidSymbolAfterBackspace() {
    myFixture.configureByText("a.java", """
      class A {
        Object foo() { this<caret> }
      }""");
    type(".");
    assertNotNull(getLookup());
    type("c");
    assertNotNull(getLookup());
    type("\b");
    assertNotNull(getLookup());
    type("x");
    assertNull(getLookup());
  }

  public void testDoubleLiteralInField() {
    myFixture.configureByText("a.java", """
      public interface Test {
        double FULL = 1.0<caret>
      }""");
    type("d");
    assertNull(getLookup());
  }

  public void testCancellingDuringCalculation() {
    myFixture.configureByText("a.java", """
      class Aaaaaaa {}
      public interface Test {
        <caret>
      }""");
    edt(() -> myFixture.type("A"));
    joinAutopopup();
    final LookupImpl first = getLookup();
    assertNotNull(first);
    edt(() -> {
      assertEquals(first, getLookup());
      getLookup().hide();
      myFixture.type("a");
    });
    joinAutopopup();
    joinAutopopup();
    joinAutopopup();
    assertNotEquals(getLookup(), first);
  }

  public void testDuringCompletionMustFinish() throws InterruptedException {
    registerContributor(LongReplacementOffsetContributor.class);

    edt(() -> myFixture.addFileToProject("directory/foo.txt", ""));
    myFixture.configureByText("a.java", "public interface Test { RuntiExce<caret>xxx }");
    myFixture.completeBasic();
    while (getLookup().getItems().isEmpty()) {
      Thread.sleep(10);
      edt(() -> getLookup().refreshUi(false, false));
    }

    edt(() -> myFixture.type("\t"));
    myFixture.checkResult("public interface Test { RuntimeException<caret>x }");
  }

  private void registerContributor(final Class<?> contributor, LoadingOrder order) {
    registerCompletionContributor(contributor, myFixture.getTestRootDisposable(), order);
  }

  private void registerContributor(final Class<?> contributor) {
    registerContributor(contributor, LoadingOrder.LAST);
  }

  public static void registerCompletionContributor(Class<?> contributor, Disposable parentDisposable, LoadingOrder order) {
    DefaultPluginDescriptor pluginDescriptor = new DefaultPluginDescriptor("registerCompletionContributor");
    CompletionContributorEP extension = new CompletionContributorEP("any", contributor.getName(), pluginDescriptor);
    CompletionContributor.EP.getPoint().registerExtension(extension, order, parentDisposable);
  }

  public void testLeftRightMovements() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>ter   x
        }
      }""");
    type("i");
    int offset = getCaretOffset();
    assertContains("iterable", "if", "int");

    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    assertEquals(getCaretOffset(), offset + 1);
    joinAutopopup();
    joinCompletion();
    assertFalse(getLookup().isCalculating());
    assertContains("iterable");
    TestCase.assertEquals("iterable", getLookup().getCurrentItem().getLookupString());

    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
    assertEquals(getCaretOffset(), offset);
    joinAutopopup();
    joinCompletion();
    assertFalse(getLookup().isCalculating());
    assertContains("iterable", "if", "int");
    TestCase.assertEquals("iterable", getLookup().getCurrentItem().getLookupString());

    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
    joinAutopopup();
    joinCompletion();
    assertFalse(getLookup().isCalculating());
    assertTrue(getLookup().getItems().size() > 3);

    for (int i = 0; i <= "iter".length(); i++) {
      edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    }

    assertNull(getLookup());
  }

  private int getCaretOffset() {
    final JavaCodeInsightTestFixture fixture = myFixture;
    return ReadAction.compute(() -> fixture.getEditor().getCaretModel().getOffset());
  }

  public void testMulticaretLeftRightMovements() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>ter   x
          <caret>ter   x
        }
      }""");
    type("i");
    assertNotNull(getLookup());

    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    myFixture.checkResult("""
                               class Foo {
                                 void foo(String iterable) {
                                   it<caret>er   x
                                   it<caret>er   x
                                 }
                               }""");
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertFalse(getLookup().isCalculating());

    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
    myFixture.checkResult("""
                               class Foo {
                                 void foo(String iterable) {
                                   i<caret>ter   x
                                   i<caret>ter   x
                                 }
                               }""");
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertFalse(getLookup().isCalculating());
  }

  public void testMulticaretRightMovementWithOneCaretAtDocumentEnd() {
    myFixture.configureByText("a.java",
                              """
                                  class Foo {
                                    void foo(String iterable) {
                                      ter   x
                                    }
                                  }
                                  <caret>""");
    edt(() -> {
      int primaryCaretOffset = myFixture.getEditor().getDocument().getText().indexOf("ter   x");
      myFixture.getEditor().getCaretModel().addCaret(myFixture.getEditor().offsetToVisualPosition(primaryCaretOffset));
    });

    type("i");
    assertNotNull(getLookup());

    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    myFixture.checkResult(
      """
              class Foo {
                void foo(String iterable) {
                  it<caret>er   x
                }
              }
              i<caret>""");
  }

  public void testTypingInAnotherEditor() {
    myFixture.configureByText("a.java", "");
    type("c");
    assertNotNull(getLookup());

    final Ref<Editor> another = Ref.create();
    final Runnable wca = () -> {
      WriteCommandAction.writeCommandAction(getProject())
        .run(() -> TypedAction.getInstance().getHandler().execute(another.get(), 'x', DataManager.getInstance().getDataContext()));
    };

    try {
      edt(() -> {
        assertFalse(getLookup().isCalculating());
        getLookup().hide();
        PsiFile file = myFixture.addFileToProject("b.java", "");
        another.set(EditorFactory.getInstance().createEditor(file.getViewProvider().getDocument(), getProject()));
        wca.run();
        assertEquals("x", another.get().getDocument().getText());
      });
      joinAutopopup();
      joinCompletion();
      LookupImpl l1 = (LookupImpl)LookupManager.getActiveLookup(another.get());
      if (l1 != null) {
        ThreadUtil.printThreadDump();
        System.out.println(l1.getItems());
        System.out.println(l1.isCalculating());
        System.out.println(myFixture.getEditor());
        System.out.println(another.get());
        System.out.println(CompletionServiceImpl.getCompletionPhase());
        fail(String.valueOf(l1.getItems()));
      }

      type("l");
      assertNotNull(getLookup());
    }
    finally {
      edt(() -> EditorFactory.getInstance().releaseEditor(another.get()));
    }
  }

  public static class LongReplacementOffsetContributor extends CompletionContributor {
    @Override
    public void duringCompletion(@NotNull CompletionInitializationContext cxt) {
      try {
        Thread.sleep(500);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      ProgressManager.checkCanceled();
      cxt.setReplacementOffset(cxt.getReplacementOffset() - 1);
    }
  }

  public static class LongContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      result.runRemainingContributors(parameters, true);
      try {
        Thread.sleep(500);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void testExplicitCompletionOnEmptyAutopopup() {
    myFixture.configureByText("a.java", "<caret>");
    type("cccccc");
    myFixture.completeBasic();
    joinCompletion();
    assertNull(getLookup());
  }

  public void testNoSingleTemplateLookup() {
    myFixture.configureByText("a.java", "class Foo { psv<caret> }");
    type("m");
    assertNull(String.valueOf(myFixture.getLookupElementStrings()), getLookup());
  }

  public void testTemplatesWithNonImportedClasses() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    myFixture.addClass("package foo.bar; public class ToArray {}");
    try {
      myFixture.configureByText("a.java", "class Foo {{ foo(<caret>) }}");
      type("toar");
      assertNotNull(getLookup());
      assertTrue(myFixture.getLookupElementStrings().contains("toar"));
      assertTrue(myFixture.getLookupElementStrings().contains("ToArray"));
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    }
  }

  public void testTemplateSelectionBySpace() {
    myFixture.configureByText("a.java", """
      class Foo {
          int ITER = 2;
          int itea = 2;
      
          static {
              it<caret>
          }
      }""");
    type("er ");
    assertFalse(myFixture.getEditor().getDocument().getText().contains("for "));
  }

  public void testNewClassParenthesis() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    try{
      myFixture.configureByText("a.java", " class Foo { static { new <caret> } } ");
      type("aioo(");
      assertTrue(myFixture.getEditor().getDocument().getText().contains("new ArrayIndexOutOfBoundsException()"));
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    }
  }

  public void testUnknownMethodParenthesis() {
    myFixture.configureByText("a.java", " class Foo { { <caret> } } ");
    type("filinpstr(");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("filinpstr()"));
  }

  public void testNoAutopopupAfterSpace() {
    myFixture.configureByText("a.java", " class Foo { { int newa; <caret> } } ");
    edt(() -> myFixture.type("new "));
    joinAutopopup();
    joinCompletion();
    assertNull(getLookup());
  }

  public void testRestartAndTypingDuringCopyCommit() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int newa; <caret> } } ");
    myFixture.type("n");
    joinAutopopup();
    myFixture.type("e");
    joinCommit();// original commit
    myFixture.type("w");
    joinAutopopup();
    joinCompletion();
    myFixture.type("\n");
    myFixture.checkResult(" class Foo { { int newa; new <caret> } } ");
    assertNull(getLookup());
  }

  public void testAutoRestartAndTypingDuringCopyCommit() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int iteraaa; <caret> } } ");
    type("ite");
    assertFalse(myFixture.getLookupElementStrings().contains("iter"));
    myFixture.type("r");
    joinCommit();
    myFixture.type("a");
    joinAutopopup();
    joinCompletion();
    myFixture.type("\n");
    myFixture.checkResult(" class Foo { { int iteraaa; iteraaa<caret> } } ");
    assertNull(getLookup());
  }

  public void testChoosingItemDuringCopyCommit() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int iteraaa; <caret> } } ");
    type("ite");
    assertFalse(myFixture.getLookupElementStrings().contains("iter"));
    assertTrue(myFixture.getLookupElementStrings().contains("iteraaa"));
    myFixture.type("r");
    joinCommit();
    myFixture.type("a.");
    myFixture.checkResult(" class Foo { { int iteraaa; iteraaa.<caret> } } ");
  }

  public void testRestartWithInvisibleLookup() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int abcdef; <caret> } } ");
    myFixture.type("a");
    joinAutopopup();
    assertNotNull(getLookup());
    edt(() -> myFixture.type("bc"));
    joinAutopopup();
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertTrue(getLookup().isShown());
  }

  public void testRestartWithVisibleLookup() throws InterruptedException {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);

    myFixture.configureByText("a.java", " class Foo { static { int abcdef, abcdefg; ab<caret> } } ");
    myFixture.completeBasic();
    while (!getLookup().isShown()) {
      Thread.sleep(1);
    }

    LookupImpl l = getLookup();
    edt(() -> {
      if (!getLookup().isCalculating()) {
        System.out.println("testRestartWithVisibleLookup couldn't be faster than LongContributor");
      }
      myFixture.type("c");
    });
    joinCommit(() -> myFixture.type("d"));
    joinAutopopup();
    joinCompletion();
    assertEquals(getLookup(), l);
    assertFalse(getLookup().isCalculating());
    assertTrue(getLookup().isShown());
  }

  public void testEveryPossibleWayToTypeIf() {
    String src = "class Foo { { int ifa; <caret> } }";
    String result = "class Foo { { int ifa; if <caret> } }";
    int actions = 4;

    for (int a1 = 0; a1 <= actions; a1++) {
      for (int a2 = 0; a2 <= actions; a2++) {
        myFixture.configureByText(a1 + " " + a2 + " .java", src);
        myFixture.getEditor().getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            if (e.getNewFragment().toString().contains("a")) {
              TestCase.fail(e.toString());
            }
          }
        });
        myFixture.type("i");
        joinSomething(a1);
        myFixture.type("f");
        joinSomething(a2);
        myFixture.type(" ");

        joinAutopopup();
        joinCompletion();
        try {
          myFixture.checkResult(result);
        }
        catch (Exception e) {
          throw new RuntimeException("actions: " + a1 + " " + a2, e);
        }

        assertNull(getLookup());
      }
    }


    for (int a1 = 0; a1 <= actions; a1++) {
      myFixture.configureByText(a1 + " if .java", src);
      edt(() -> myFixture.type("if"));
      joinSomething(a1);
      myFixture.type(" ");
      joinAutopopup();
      joinCompletion();
      try {
        myFixture.checkResult(result);
      }
      catch (Exception e) {
        throw new RuntimeException("actions: " + a1, e);
      }

      assertNull(getLookup());
    }


    for (int a1 = 0; a1 <= actions; a1++) {
      myFixture.configureByText(a1 + " if .java", src);
      myFixture.type("i");
      joinSomething(a1);

      edt(() -> myFixture.type("f "));

      joinAutopopup();
      joinCompletion();
      try {
        myFixture.checkResult(result);
      }
      catch (Exception e) {
        throw new RuntimeException("actions: " + a1, e);
      }

      assertNull(getLookup());
    }
  }

  public void testNonFinishedParameterComma() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(int aaa, int aaaaa) { }
        void bar(int aaa, int aaaaa) { foo(<caret>) }
      }""");
    type("a");
    type("a");
    type(",");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(aaa, )"));
  }

  public void testFinishedParameterComma() {
    myFixture.configureByText("a.java", " class Foo { void foo(int aaa, int aaaaa) { foo(<caret>) } } ");
    type("aaa,");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(aaa,)"));
  }

  public void testNonFinishedVariableEq() {
    myFixture.configureByText("a.java", " class Foo { void foo(int aaa, int aaaaa) { <caret> } } ");
    type("a=");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("aaa = "));
  }

  public void testFinishedVariableEq() {
    myFixture.configureByText("a.java", " class Foo { void foo(int aaa, int aaaaa) { <caret> } } ");
    type("aaa=");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("aaa="));
  }

  public void testCompletionWhenLiveTemplateAreNotSufficient() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class Foo {
          static {
              Iterable<String> l1 = null;
              Iterable<String> l2 = null;
              Object asdf = null;
              iter<caret>
          }
      }""");
    type("\t");
    assertEquals(List.of("l2", "l1"), myFixture.getLookupElementStrings());
    type("as");
    assertNotNull(getLookup());
    assertContains("asdf", "assert");
    type("\n.");
    assertNotNull(getLookup());
    assertTrue(myFixture.getLookupElementStrings().contains("hashCode"));
    assertTrue(myFixture.getEditor().getDocument().getText().contains("asdf."));
  }

  public void testNoWordCompletionAutoPopup() {
    myFixture.configureByText("a.java", "class Bar { void foo() { \"f<caret>\" }}");
    type("o");
    assertNull(getLookup());
  }

  public void testMethodNameRestart() {
    myFixture.configureByText("a.java", """
      public class Bar {
          private static List<java.io.File> getS<caret>
      }
      """);
    type("ta");
    assertNull(getLookup());
  }

  public void testTargetElementInLookup() {
    myFixture.configureByText("a.java", """
      class Foo {
        void x__foo() {}
        void bar() {
          <caret>
        }
        void x__goo() {}
      }
      """);
    final PsiClass cls = ReadAction.compute(() -> ((PsiJavaFile)myFixture.getFile()).getClasses()[0]);

    PsiMethod[] methods = ReadAction.compute(() -> cls.getMethods());
    final PsiMethod foo = methods[0];
    final PsiMethod goo = methods[2];
    type("x");
    assertContains("x__foo", "x__goo");
    edt(() -> {
      assertEquals(foo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
      assertEquals(goo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
    });

    type("_");
    myFixture.assertPreferredCompletionItems(1, "x__foo", "x__goo");
    edt(() -> {
      assertEquals(goo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
      assertEquals(foo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
    });
  }

  public void testExplicitAutocompletionAfterAutoPopup() {
    myFixture.configureByText("a.java", "class Foo <caret>");
    type("ext");

    edt(() -> myFixture.completeBasic());
    assertNull(String.valueOf(myFixture.getLookupElementStrings()), getLookup());
    myFixture.checkResult("class Foo extends <caret>");
  }

  public void testExplicitMultipleVariantCompletionAfterAutoPopup() {
    myFixture.configureByText("a.java", "class Foo {<caret>}");
    type("pr");

    edt(() -> myFixture.completeBasic());
    myFixture.checkResult("class Foo {pr<caret>}");

    ReadAction.run(() -> {
      JList<LookupElement> list = getLookup().getList();
      for (LookupElement item : getLookup().getItems()) {
        list.getCellRenderer().getListCellRendererComponent(list, item, 0, false, false);
      }
    });
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("private", "protected")));
  }

  public void testExactMatchesFirst() {
    myFixture.configureByText("a.java", """
      public class UTest {
          void nextWord() {}
      
          void foo() {
              n<caret>
          }
      }""");
    type("ew");
    assertEquals(myFixture.getLookupElementStrings(), List.of("new", "nextWord"));
  }

  public void testExactMatchesTemplateFirst() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      public class Test {
          void itar() {}
      
          void foo() {
              ita<caret>
          }
      }""");
    type("r");
    assertEquals(myFixture.getLookupElementStrings(), List.of("itar", "itar"));
    assertTrue(myFixture.getLookup().getCurrentItem() instanceof LiveTemplateLookupElement);
  }

  public void testMoreRecentExactMatchesTemplateFirst() {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("itar", "myGroup", null);
    JavaCodeContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Statement.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      public class Test {
          void foo() {
              ita<caret>
          }
      }""");
    type("r");
    myFixture.assertPreferredCompletionItems(0, "itar", "itar");
  }

  public void testUpdatePrefixMatchingOnTyping() {
    myFixture.addClass("class CertificateEncodingException extends Exception {}");
    myFixture.addClass("class CertificateException extends Exception {}");
    myFixture.configureByText("a.java", "class Foo {<caret>}");
    type("CertificateExce");
    assertEquals(myFixture.getLookupElementStrings(), List.of("CertificateException", "CertificateEncodingException"));
  }

  public void testNoClassesInUnqualifiedImports() {
    myFixture.addClass("package xxxxx; public class Xxxxxxxxx {}");
    myFixture.configureByText("a.java", "package foo; import <caret>");
    type("xxx");
    assertNull(getLookup());
  }

  public void testPopupAfterDotAfterPackage() {
    myFixture.configureByText("a.java", "<caret>");
    type("import jav");
    assertNotNull(getLookup());
    type(".");
    assertNotNull(getLookup());
  }

  public void testPopupInShebang() {
    myFixture.configureByText("app", """
      #! /usr/bin/java --source 16
      record App() {
          public  static void main(String[] args) {
            args<caret>
          }
      }""");
    type(".");
    assertNotNull(getLookup());
  }

  public void testSamePrefixIgnoreCase() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}");
    myFixture.configureByText("a.java", "import xxxxx.*; class Foo { S<caret> }");
    type("Ystem");
    myFixture.assertPreferredCompletionItems(1, "System", "SYSTEM_EXCEPTION");
  }

  public void testSamePrefixIgnoreCase2() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}");
    myFixture.addClass("package xxxxx; public class SYstem {}");
    myFixture.configureByText("a.java", "import xxxxx.*; class Foo { S<caret> }");
    type("Ystem");
    myFixture.assertPreferredCompletionItems(0, "SYstem", "System", "SYSTEM_EXCEPTION");
  }

  private FileEditor openEditorForUndo() {
    final Ref<FileEditor> editor = Ref.create();
    edt(() -> editor.set(FileEditorManager.getInstance(getProject()).openFile(myFixture.getFile().getVirtualFile(), false)[0]));
    final UndoManagerImpl manager = (UndoManagerImpl)UndoManager.getInstance(getProject());
    manager.setOverriddenEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor(@Nullable Project project) {
        return editor.get();
      }
    });
    disposeOnTearDown(() -> manager.setOverriddenEditorProvider(null));
    return editor.get();
  }

  public void testAutopopupTypingUndo() {
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    final FileEditor editor = openEditorForUndo();
    type("aioobeeee");
    edt(() -> UndoManager.getInstance(getProject()).undo(editor));
    assertFalse(myFixture.getEditor().getDocument().getText().contains("aioo"));
  }

  public void testNoLiveTemplatesAfterDot() {
    myFixture.configureByText("a.java", "import java.util.List; class Foo { static { List<?> t; t.<caret> }}");
    type("toar");
    assertEquals(myFixture.getLookupElementStrings(), List.of("toArray", "toArray"));
  }

  public void testTypingFirstVarargDot() {
    myFixture.configureByText("a.java", "class Foo { void foo(Foo<caret>[] a) { } class Bar {}}");
    type(".");
    assertNull(getLookup());
  }

  public void testMulticaret() {
    doTestMulticaret("""
                       class Foo {{
                         <selection>t<caret></selection>x;
                         <selection>t<caret></selection>x;
                       }}""", "\n", """
                       class Foo {{
                         toString()<caret>x;
                         toString()<caret>x;
                       }}""");
  }

  public void testMulticaretTab() {
    doTestMulticaret("""
                       class Foo {{
                         <selection>t<caret></selection>x;
                         <selection>t<caret></selection>x;
                       }}""", "\t", """
                       class Foo {{
                         toString()<caret>;
                         toString()<caret>;
                       }}""");
  }

  public void testMulticaretBackspace() {
    doTestMulticaret("""
                       class Foo {{
                         <selection>t<caret></selection>;
                         <selection>t<caret></selection>;
                       }}""", "\b\t", """
                       class Foo {{
                         toString()<caret>;
                         toString()<caret>;
                       }}""");
  }

  private void doTestMulticaret(final String textBefore, final String toType, final String textAfter) {
    myFixture.configureByText("a.java", textBefore);
    type("toStr");
    assertNotNull(getLookup());
    type(toType);
    myFixture.checkResult(textAfter);
  }

  public void test_two_non_imported_classes_when_space_selects_first_autopopup_item() {
    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.configureByText("a.java", "class Foo extends <caret>");
    type("Abcde ");
    myFixture.checkResult("import foo.Abcdefg;\n\nclass Foo extends Abcdefg <caret>");
  }

  public void test_two_non_imported_classes_when_space_does_not_select_first_autopopup_item() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.addClass("package bar; public class Abcdefg {}");
    myFixture.configureByText("a.java", "class Foo extends <caret>");
    type("Abcde");
    assertEquals(2, getLookup().getItems().size());
    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN));
    type(" ");
    myFixture.checkResult("""
                            import foo.Abcdefg;

                            class Foo extends Abcdefg <caret>""");
  }

  public void testTwoNonImportedClasses_() {
    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.addClass("package bar; public class Abcdefg {}");
    myFixture.configureByText("a.java", "class Foo extends <caret>");
    type("Abcde");
    assertEquals(2, getLookup().getItems().size());
  }

  public void testClassNameInProperties() {
    myFixture.addClass("package java.langa; public class Abcdefg {}");
    myFixture.configureByText("a.properties", "key.11=java<caret>");
    type(".");
    assertNotNull(getLookup());
    type("lang");
    assertTrue(myFixture.getLookupElementStrings().size() >= 2);
    type(".");
    assertNotNull(getLookup());
    edt(() -> myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("lang")));
    assertNull(getLookup());
    type("i");
    assertTrue(myFixture.getLookupElementStrings().contains("io"));
  }

  public void testEnteringLabel() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    type("FIS:");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("FIS:"));
  }

  public void testSoutvTemplate() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    type("soutv\tgetcl.");
    myFixture.checkResult("class Foo {{\n    System.out.println(\"getClass(). = \" + getClass().<caret>);\n}}");
  }

  public void testReturnLParen() {
    myFixture.configureByText("a.java", "class Foo { int foo() { <caret> }}");
    type("re(");
    myFixture.checkResult("class Foo { int foo() { re(<caret>) }}");
  }

  public void testAmbiguousClassQualifier() {
    myFixture.addClass("package foo; public final class Util<T> { public static void foo() {} public static final int CONSTANT = 2; }");
    myFixture.addClass("package bar; public final class Util { public static void bar() {} }");
    myFixture.configureByText("a.java", "class Foo { static { Util<caret> }}");
    type(".");
    assertEquals(Set.copyOf(myFixture.getLookupElementStrings()), Set.of("Util.bar", "Util.CONSTANT", "Util.foo"));

    LookupElement constant = ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("Util.CONSTANT"));
    LookupElementPresentation p = NormalCompletionTestCase.renderElement(constant);
    assertEquals("Util.CONSTANT", p.getItemText());
    assertEquals(" ( = 2) foo", p.getTailText());
    assertEquals("int", p.getTypeText());

    type("fo\n");
    myFixture.checkResult("""
                            import foo.Util;

                            class Foo { static {
                                Util.foo();<caret> }}""");
  }

  public void testPackageQualifier() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.addClass("package com.too; public class Util {}");
    myFixture.configureByText("a.java", "class Foo { void foo(Object command) { <caret> }}");
    type("com.t");
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("too", "command.toString")));
  }

  public void testUnfinishedString() {
    myFixture.configureByText("a.java", """
      // Date
      class Foo {
        String s = "<caret>
        String s2 = s;
      }
      """);
    type("D");
    assertNull(getLookup());
  }

  public void testVarargParenthesis() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(File... files) { }
        { foo(new <caret>) }
      }
      """);
    type("File(");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("new File()"));
  }

  public void test_inaccessible_class_in_another_package_shouldn_t_prevent_choosing_by_space() {
    myFixture.addClass("package foo; class b {}");
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    type("b");
    final LookupImpl impl = getLookup();
    final LookupElement item = (impl == null ? null : impl.getCurrentItem());
    assertEquals("boolean", (item == null ? null : item.getLookupString()));
    type(" ");
    myFixture.checkResult("class Foo {{ boolean <caret> }}");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
      UISettings.getInstance().setSortLookupElementsLexicographically(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testBackspaceShouldShowPreviousVariants() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    myFixture.configureByText("a.java", "class Foo{ void foo(int itera, int itex) { it<caret> }}");
    type("e");
    myFixture.assertPreferredCompletionItems(0, "itera", "itex");
    type("r");
    myFixture.assertPreferredCompletionItems(0, "iter", "itera");
    type("\b");
    myFixture.assertPreferredCompletionItems(0, "itera", "itex");
  }

  public void testBackspaceUntilDot() {
    myFixture.configureByText("a.java", "class Foo{ void foo(String s) { s<caret> }}");
    type(".sub");
    assertNotNull(myFixture.getLookupElementStrings());
    type("\b\b\b");
    assertNotNull(getLookup());
    type("\b");
    assertNull(getLookup());
  }

  public void testQuickBackspaceEnter() {
    myFixture.configureByText("a.java", "<caret>");
    type("cl");
    assertEquals(myFixture.getLookupElementStrings(), List.of("class"));
    myFixture.type("\b\n");
    myFixture.checkResult("class <caret>");
  }

  public void test_new_primitive_array_in_Object_variable() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    myFixture.configureByText("a.java", "\nclass Foo {\n  void foo() {\n    Object o = new <caret>\n  }\n}\n");
    type("int");
    myFixture.assertPreferredCompletionItems(0, "int", "Integer");
    assertEquals(1, ((PsiTypeLookupItem)myFixture.getLookupElements()[0]).getBracketsCount());
    type("[");
    myFixture.checkResult("\nclass Foo {\n  void foo() {\n    Object o = new int[<caret>]\n  }\n}\n");
  }

  public void test_middle_matching_and_overwrite() {
    myFixture.configureByText("a.java", "\nclass ListConfigKey {\n  void foo() {\n    <caret>\n  }\n}\n");
    type("CK\t");
    myFixture.checkResult("\nclass ListConfigKey {\n  void foo() {\n    ListConfigKey<caret>\n  }\n}\n");

  }

  public void testPreselectMostRelevantInTheMiddleAlpha() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.configureByText("a.java", """
      class Foo {
        void setText() {}
        void setHorizontalText() {}
        void foo() {
          <caret>
        }
      
      }
      """);
    type("sette");
    myFixture.assertPreferredCompletionItems(1, "setHorizontalText", "setText");
    edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP));
    myFixture.assertPreferredCompletionItems(0, "setHorizontalText", "setText");
  }

  public void test_pressing_enter_while_autopopup_is_calculating_variants_should_cancel_autopopup() {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    myFixture.type("a");
    joinAutopopup();
    type("\n");
    assertNull(getLookup());
  }

  public void test_pressing_enter_and_a_letter_while_autopopup_is_calculating_variants_should_restart_autopopup() {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    myFixture.type("a");
    joinAutopopup();
    myFixture.type("\nf");
    joinCompletion();
    assertNotNull(getLookup());
  }

  public void test_a_random_write_action_shouldn_t_cancel_autopopup() {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    myFixture.type("a");
    joinAutopopup();
    edt(() -> ApplicationManager.getApplication().runWriteAction(() -> {}));
    joinCompletion();
    assertNotNull(getLookup());
  }

  public void test_typing_during_restart_commit_document() {
    String longText = "\nfoo(); bar();".repeat(100);
    myFixture.configureByText("a.java", "class Foo { void foo(int xb, int xbde) { <caret>; " + longText + " }}");
    myFixture.type("x");
    joinAutopopup();
    myFixture.type("b");
    myTester.joinCommit();
    myFixture.type("c");
    joinCompletion();
    assertNull(getLookup());
  }

  public void test_no_name_autopopup_in_live_template() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class F {
        String nameContainingIdentifier;
      <caret>
      }""");

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("m", "user", "void foo(String $V1$) {}");
    template.addVariable("V1", "", "\"s\"", true);

    edt(() -> CommandProcessor.getInstance().executeCommand(
      getProject(), () -> manager.startTemplate(myFixture.getEditor(), template), null, null));

    type("name");
    assertNull(myFixture.getLookupElementStrings());
    assertNull(getLookup());
  }

  public void test_template_prefix_is_better_than_middle_matches() {
    myFixture.configureByText("a.java", """
      class Cls {
        void foo() {
          <caret>
        }
        void mySout() {}
      }
      """);
    type("sout");
    myFixture.assertPreferredCompletionItems(0, "sout", "mySout");
  }

  public void test_single_overriding_getter() {
    myFixture.configureByText("a.java", """
      public class Foo {
          public int getField() {}
      }
      
      class X extends Foo {
          int field;
      
          <caret>
      }
      """);
    type("getf");
    assertEquals(myFixture.getLookupElementStrings(), List.of("public int getField"));
  }

  public void test_live_template_quick_doc() {
    myFixture.configureByText("a.java", """
      class Cls {
        void foo() {
          <caret>
        }
        void mySout() {}
      }
      """);
    type("sout");
    assertNotNull(getLookup());
    assertTrue(myFixture.getLookupElementStrings().contains("sout"));

    LiveTemplateDocumentationProvider docProvider = new LiveTemplateDocumentationProvider();
    PsiElement docElement = docProvider.getDocumentationElementForLookupItem(myFixture.getPsiManager(), getLookup().getCurrentItem(), null);
    assertEquals("sout", ((NavigatablePsiElement)docElement).getPresentation().getPresentableText());
    assertTrue(docProvider.generateDoc(docElement, docElement).contains("System.out"));
  }

  public void test_finishing_class_reference_property_value_completion_with_dot_opens_autopopup() {
    myFixture.configureByText("a.properties", "myprop=ja<caret>");
    type("v");
    myFixture.assertPreferredCompletionItems(0, "java");
    getLookup().setLookupFocusDegree(LookupFocusDegree.FOCUSED);
    type(".");
    myFixture.checkResult("myprop=java.<caret>");
    assertNotNull(getLookup());
  }

  public void test_live_template_without_description() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("tpl", "user", null);
    JavaCodeContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Statement.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", """
      class Foo {
       int tplMn;
      \s
       { <caret> }
      }
      """);
    type("tpl");
    myFixture.assertPreferredCompletionItems(0, "tpl", "tplMn");

    LookupElementPresentation p = NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[0]);
    assertEquals("tpl", p.getItemText());
    assertNull(p.getTailText());
    assertTrue(p.getTypeText().isEmpty());
  }

  public void test_autopopup_after_package_completion() {
    myFixture.addClass("package foo.bar.goo; class Foo {}");
    myFixture.configureByText("a.java", "class Foo { { foo.<caret> } }");
    type("b");
    assertEquals(myFixture.getLookupElementStrings(), List.of("bar"));
    assertEquals("bar.", NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[0]).getItemText());
    myFixture.type("\n");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo.bar. "));
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertEquals(myFixture.getLookupElementStrings(), List.of("goo"));
  }

  public void test_in_column_selection_mode() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}""");
    edt(() -> ((EditorEx)myFixture.getEditor()).setColumnMode(true));
    type("toStr");
    assertNotNull(getLookup());
  }

  public void test_show_popup_with_single_live_template_if_show_live_tempate_in_completion_option_is_enabled() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class Foo {
        static {
          ita<caret>
        }
      }""");
    type("r");
    assertNull(getLookup());

    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    type("\br");
    assertNotNull(getLookup());
    assertEquals(myFixture.getLookupElementStrings(), List.of("itar"));
  }

  public void test_expand_class_list_when_typing_more_or_moving_caret() {
    myFixture.addClass("package foo; public class KimeFamilyRange {}");
    myFixture.addClass("package foo; public class FamiliesRangesMetaData {}");
    myFixture.addClass("public class KSomethingInCurrentPackage {}");
    myFixture.configureByText("a.java", "class Foo { <caret> }");

    type("F");
    assertFalse(myFixture.getLookupElementStrings().contains("KimeFamilyRange"));

    type("aRa");
    myFixture.assertPreferredCompletionItems(0, "FamiliesRangesMetaData", "KimeFamilyRange");

    for (int i = 0; i < 4; i++) {
      edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
      joinCompletion();
    }
    assertFalse(myFixture.getLookupElementStrings().contains("KimeFamilyRange"));

    type("K");

    for (int i = 0; i < 4; i++) {
      edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
      joinCompletion();
    }

    myFixture.assertPreferredCompletionItems(0, "KimeFamilyRange");
  }

  public void test_show_autopopup_when_typing_digit_after_letter() {
    myFixture.configureByText("a.java", "class Foo { static { int a42; a<caret> }}");
    type("4");
    assertNotNull(getLookup());
  }

  public void test_autopopup_after_new() {
    myFixture.configureByText("a.java", "class Foo { static { java.util.List<String> l = new<caret> }}");
    type(" ");
    assertNotNull(getLookup());
    List<LookupElement> firstItems = Arrays.asList(myFixture.getLookupElements()).subList(0, 4);
    boolean inheritors = ReadAction.compute(() -> ContainerUtil.all(
      firstItems, it -> InheritanceUtil.isInheritor((PsiClass)it.getObject(), CommonClassNames.JAVA_UTIL_LIST)));
    assertTrue(inheritors);
  }

  public void test_prefer_previously_selected_despite_many_namesakes() {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());

    int count = 400;
    final int toSelect = 390;
    for (int i = 0; i <= count; i++) {
      myFixture.addClass("package p" + i + "; public class MyClass {}");
    }

    myFixture.configureByText("a.java", "class C extends <caret>");
    type("MyCla");
    myFixture.assertPreferredCompletionItems(0, ArrayUtil.toStringArray(Collections.nCopies(count, "MyClass")));

    edt(() -> {
      assertEquals(" p" + toSelect, NormalCompletionTestCase.renderElement(myFixture.getLookup().getItems().get(toSelect)).getTailText());
      CompletionSortingTestCase.imitateItemSelection(myFixture.getLookup(), toSelect);
      myFixture.getLookup().hideLookup(true);
    });

    type("s");
    myFixture.assertPreferredCompletionItems(0, ArrayUtil.toStringArray(Collections.nCopies(count, "MyClass")));
    edt(() -> {
      assertEquals(" p" + toSelect, NormalCompletionTestCase.renderElement(myFixture.getLookup().getItems().get(0)).getTailText());
    });
  }

  public void test_show_popup_when_completing_property_key() {
    myFixture.createFile("PropertyKey.properties", "foo.bar = 1");
    myFixture.configureByText("a.java", """
      import org.jetbrains.annotations.PropertyKey;

      public class Foo {
          public static void message(@PropertyKey(resourceBundle = "PropertyKey") String key) {}

          void test() {
              message("fo<caret>");
          }
      }
      """);
    type("o");
    assertNotNull(getLookup());
  }
}
