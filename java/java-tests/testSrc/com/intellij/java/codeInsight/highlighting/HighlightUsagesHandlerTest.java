// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.highlighting;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeInsight.daemon.JdkDaemonAnalyzerFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.java.codeInsight.daemon.JdkDaemonAnalyzerFixture.jdkDaemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class HighlightUsagesHandlerTest {
  private final TestFixture<JdkDaemonAnalyzerFixture> daemonFixture =
    jdkDaemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath(), () -> IdeaTestUtil.getMockJdk17(), LanguageLevel.JDK_16); // records are needed

  @Test
  public void testHighlightImport() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("import", "List", "List", "List", "List", "List");
    checkUnselect();
  }

  @Test
  public void testHighlightStaticImport() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("import", "abs", "abs", "pow");
    checkUnselect();
  }

  @Test
  public void testSimpleThrows() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "Exception");
    checkUnselect();
  }

  @Test
  public void testThrowsExpression() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "(Exception)detail");
    checkUnselect();
  }

  @Test
  public void testThrowsReference() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "detail");
    checkUnselect();
  }

  @Test
  public void testUnselectUsage() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("foo", "foo", "foo");
    checkUnselect();
  }

  @Test
  public void testHighlightOverridden() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("extends", "foo");
    checkUnselect();
  }

  @Test
  public void testHighlightOverriddenImplements() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo");
    checkUnselect();
  }

  @Test
  public void testHighlightOverriddenNothing() {
    configureFile();
    ctrlShiftF7();
    assertRangeText();
    checkUnselect();
  }

  @Test
  public void testHighlightOverriddenMultiple() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo", "other");
    checkUnselect();
  }

  @Test
  public void testBreakInSwitch() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("switch", "break");
    checkUnselect();
  }

  @Test
  public void testBreakInSwitchExpr() {
    IdeaTestUtil.withLevel(daemonFixture.get().getModule(), LanguageLevel.JDK_14, ()->{
      configureFile();
      ctrlShiftF7();
      assertRangeText("switch", "yield", "yield");
      checkUnselect();
    });
  }

  @Test
  public void testBreakInDoWhile() {
    configureFile();
    ctrlShiftF7();
    assertRangeText("break", "continue", "while");
    checkUnselect();
  }

  @Test
  public void testIDEADEV28822() {
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      class Foo {
        public String foo(String s) {
          while (s.length() > 0) {
            if (s.length() < 0) {
              s = "";
              continue;
            }
            else {
            }
          }
          <caret>return s;
        }
      }""");
    ctrlShiftF7();
    assertRangeText("return s;");
  }

  @Test
  public void testReturnsInTryFinally() {
    // See IDEADEV-14028
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      class Foo {
        int foo(boolean b) {
          try {
            if (b) return 1;
          }
          finally {
            if (b) return 2;
          }
          <caret>return 3;
        }
      }""");
    ctrlShiftF7();
    assertRangeText("return 1;", "return 2;", "return 3;");
  }

  @Test
  public void testReturnsInLambda() {
    // See IDEADEV-14028
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      class Foo {
        {
          Runnable r = () -> {
                 if (true) return;
                 <caret>return;
          }
        }
      }""");
    ctrlShiftF7();
    assertRangeText("return;", "return;");
  }

  @Test
  public void testSuppressedWarningsHighlights() {
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      class Foo {
        @SuppressWarnings({"Sil<caret>lyAssignment"});
        void foo() {
            int i = 0;
            i = i;
        }
      }""");
    daemonFixture.get().enableInspectionTool(new SillyAssignmentInspection());
    ctrlShiftF7();
    assertRangeText("i");
  }

  @Test
  public void testSuppressedWarningsInInjectionHighlights() {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    MyTestInjector testInjector = new MyTestInjector(PsiManager.getInstance(fixture.getProject()));
    testInjector.injectAll(fixture.getTestRootDisposable());

    @Language("JAVA")
    String text = """

      class Foo {
        public static void a(boolean b, String c) {
           @SuppressWarnings("SillyAssignment")
           String java = "class A {{int i = 0; i = i;}}";
        }
      }""";
    fixture.configureByText( JavaFileType.INSTANCE, text);
    fixture.enableInspectionTool(new SillyAssignmentInspection());
    int offset = ReadAction.computeBlocking(() -> fixture.getFile().getText().indexOf("illyAssignment"));
    EdtTestUtil.runInEdtAndWait(() -> fixture.getEditor().getCaretModel().moveToOffset(offset));
    assertThat(fixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty();
    
    ctrlShiftF7();
    assertRangeText("i");
  }

  @Test
  public void testStaticallyImportedOverloadsFromUsage() {
    daemonFixture.get().createClass("""

                  class Foo {
                    static void foo(int a) {}
                    static void foo(int a, int b) {}
                  }""");
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      import static Foo.foo;

      class Bar {
        {
          <caret>foo(1);
        }
      }""");
    ctrlShiftF7();
    assertRangeText("foo", "foo");
  }

  @Test
  public void testStaticallyImportedOverloadsFromImport() {
    daemonFixture.get().createClass("""

                  class Foo {
                    static void foo(int a) {}
                    static void foo(int a, int b) {}
                  }""");
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      import static Foo.<caret>foo;

      class Bar {
        {
          foo(1);
        }
      }""");
    ctrlShiftF7();
    assertRangeText("foo", "foo", "foo"); //import highlighted twice: for each overloaded usage target
  }

  @Test
  public void testIdentifierHighlighterForStaticImports() {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(fixture.getProject(), ()->{
      fixture.createClass("""

                      class Foo {
                        static void foo(int a) {}
                        static void foo(int a, int b) {}
                      }""");
      daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

        import static Foo.fo<caret>o;

        class Bar {
          {
            foo(1);
          }
        }""");

      IdentifierHighlighterPassFactory.waitForIdentifierHighlighting(fixture.getEditor());

      assertEquals(2, getIdentifierHighlighters()
        .stream()
        .filter(info -> fixture.getFile().getText().substring(info.startOffset, info.endOffset).equals("foo"))
        .count());
    }));
  }

  @NotNull
  List<HighlightInfo> getIdentifierHighlighters() {
    return Arrays.stream(daemonFixture.get().getEditor().getMarkupModel().getAllHighlighters())
      .map(rh -> HighlightInfo.fromRangeHighlighter(rh))
      .filter(Objects::nonNull)
      .filter(info -> info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY)
      .collect(Collectors.toList());
  }
  @Test
  public void testExceptionsInTryWithResources() {
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      import java.io.*;
      class A {
        public void test() {
          try (InputStream in = new FileInputStream("file.name")) { }
          <caret>catch (FileNotFoundException e) { throw new FileNotFoundException("no highlighting here"); }
        }
      }""");
    ctrlShiftF7();
    assertRangeText("FileInputStream", "catch");
  }

  @Test
  public void testExceptionsResourceCloser() {
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      import java.io.*;
      class A {
        public void test() {
          try (InputStream in = new FileInputStream("file.name")) { }
          <caret>catch (IOException e) { }
        }
      }""");
    ctrlShiftF7();
    assertRangeText("in", "FileInputStream", "FileInputStream", "catch");
  }

  @Test
  public void testMethodParameterEndOfIdentifier() {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(fixture.getProject(), ()-> {
      configureFile();
      fixture.doHighlighting();
      Segment[] segments = getIdentifierHighlighters().toArray(Segment.EMPTY_ARRAY);
      assertSegments(segments, "28:33 param", "60:65 param", "68:73 param");
    }));
  }

  @Test
  public void testRecordComponents() {
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      record A(String s) {
        void test() {
          <caret>s();
          s();
          String a = s;
        }
      }""");
    ctrlShiftF7();
    assertRangesAndTexts("17:18 s", "42:43 s", "51:52 s", "71:72 s");
  }

  @Test
  public void testCompactConstructorParameters() {
    daemonFixture.get().configureByText(JavaFileType.INSTANCE, """

      record A(String s) {
        A {
          <caret>s;
        }
      }""");
    ctrlShiftF7();
    assertRangesAndTexts("17:18 s", "32:33 s");
  }

  /**
   * @param expected sorted by startOffset
   */
  private void assertRangesAndTexts(String... expected) {
    RangeHighlighter[] highlighters = ReadAction.computeBlocking(() -> daemonFixture.get().getEditor().getMarkupModel().getAllHighlighters());
    assertSegments(highlighters, expected);
  }

  private void assertSegments(Segment[] highlighters, String... expected) {
    String text = ReadAction.computeBlocking(() -> daemonFixture.get().getFile().getText());
    List<String> actual = Arrays.stream(highlighters)
      .sorted(Comparator.comparingInt(Segment::getStartOffset))
      .map(it -> it.getStartOffset() +
                 ":" +
                 it.getEndOffset() +
                 " " +
                 text.substring(it.getStartOffset(), it.getEndOffset()))
      .toList();
    assertSameElements(actual, Arrays.asList(expected));
  }

  private void configureFile() {
    String testName = daemonFixture.get().getTestName(false);
    daemonFixture.get().configureByFile("/codeInsight/highlightUsagesHandler/" + testName + ".java");
    //copy(getTestDataPath()+"/codeInsight/highlightUsagesHandler/" + testName + ".java", configureByFile(), testName + ".java")
    //VirtualFile file = myFixture.copyFileToProject("/codeInsight/highlightUsagesHandler/" + testName + ".java", testName + ".java");
    //configureByExistingFile(file);
  }

  private void ctrlShiftF7() {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> HighlightUsagesHandler.invoke(fixture.getProject(), fixture.getEditor(), fixture.getFile()));
  }

  private void assertRangeText(String... texts) {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    List<String> actual = ReadAction.computeBlocking(() -> {
      List<RangeHighlighter> highlighters = ContainerUtil.filter(fixture.getEditor().getMarkupModel().getAllHighlighters(), it -> it.getLayer() == HighlightManagerImpl.OCCURRENCE_LAYER);
      return ContainerUtil.map(highlighters, it -> fixture.getFile().getText().substring(it.getStartOffset(), it.getEndOffset()));
    });
    assertSameElements(actual, Arrays.asList(texts));
  }

  /**
   * Compares the sets of elements, as {@code UsefulTestCase.assertSameElements} does. It ignores a difference in the duplicates.
   */
  private static void assertSameElements(List<String> actual, List<String> expected) {
    assertEquals(new LinkedHashSet<>(expected), new LinkedHashSet<>(actual));
  }

  private void checkUnselect() {
    ctrlShiftF7();
    assertRangeText();
  }

  @Test
  public void testCaretOnExceptionInMethodThrowsDeclarationMustHighlightPlacesThrowingThisException() {
    String s = """

      import java.io.*;
      class A {
        public static void deserialize(File file) throws <caret>IOException, java.lang.RuntimeException, ClassNotFoundException {
          boolean length = file.createNewFile();
          if (length == false) throw new RuntimeException();
          file.getCanonicalPath();
          if (length == true) throw new ClassNotFoundException();
        }
      }""";
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText( JavaFileType.INSTANCE, s);

    EdtTestUtil.runInEdtAndWait(() -> {
      HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(fixture.getEditor(), fixture.getFile());
      assertNotNull(handler);
      List<PsiElement> targets = handler.getTargets();
      assertEquals(1, targets.size());

      handler.computeUsages(targets);
      List<TextRange> readUsages = handler.getReadUsages();
      List<String> expected = Arrays.asList("IOException", "file.createNewFile", "file.getCanonicalPath");
      assertEquals(expected.size(), readUsages.size());

      List<String> textUsages = ContainerUtil.map(readUsages, it -> fixture.getFile().getText().substring(it.getStartOffset(), it.getEndOffset()));
      assertSameElements(expected, textUsages);
    });
  }
}