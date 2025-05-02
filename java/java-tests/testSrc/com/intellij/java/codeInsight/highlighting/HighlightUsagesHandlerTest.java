// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HighlightUsagesHandlerTest extends DaemonAnalyzerTestCase {
  public void testHighlightImport() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("import", "List", "List", "List", "List", "List");
    checkUnselect();
  }

  public void testHighlightStaticImport() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("import", "abs", "abs", "pow");
    checkUnselect();
  }

  public void testSimpleThrows() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "Exception");
    checkUnselect();
  }

  public void testThrowsExpression() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "(Exception)detail");
    checkUnselect();
  }

  public void testThrowsReference() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "detail");
    checkUnselect();
  }

  public void testUnselectUsage() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("foo", "foo", "foo");
    checkUnselect();
  }

  public void testHighlightOverridden() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("extends", "foo");
    checkUnselect();
  }

  public void testHighlightOverriddenImplements() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo");
    checkUnselect();
  }

  public void testHighlightOverriddenNothing() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText();
    checkUnselect();
  }

  public void testHighlightOverriddenMultiple() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo", "other");
    checkUnselect();
  }

  public void testBreakInSwitch() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("switch", "break");
    checkUnselect();
  }

  public void testBreakInSwitchExpr() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_14, ()->{
      try {
        configureFile();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      ctrlShiftF7();
      assertRangeText("switch", "yield", "yield");
      checkUnselect();
    });
  }

  public void testBreakInDoWhile() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("break", "continue", "while");
    checkUnselect();
  }

  public void testIDEADEV28822() {
    configureByText(JavaFileType.INSTANCE, """

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

  public void testReturnsInTryFinally() {
    // See IDEADEV-14028
    configureByText(JavaFileType.INSTANCE, """

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

  public void testReturnsInLambda() {
    // See IDEADEV-14028
    configureByText(JavaFileType.INSTANCE, """

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

  public void testSuppressedWarningsHighlights() {
    configureByText(JavaFileType.INSTANCE, """

      class Foo {
        @SuppressWarnings({"Sil<caret>lyAssignment"});
        void foo() {
            int i = 0;
            i = i;
        }
      }""");
    enableInspectionTool(new SillyAssignmentInspection());
    ctrlShiftF7();
    assertRangeText("i");
  }

  public void testSuppressedWarningsInInjectionHighlights() {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());

    @Language("JAVA")
    String text = """

      class Foo {
        public static void a(boolean b, String c) {
           @SuppressWarnings("SillyAssignment")
           String java = "class A {{int i = 0; i = i;}}";
        }
      }""";
    configureByText( JavaFileType.INSTANCE, text);
    enableInspectionTool(new SillyAssignmentInspection());
    getEditor().getCaretModel().moveToOffset(getFile().getText().indexOf("illyAssignment"));
    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
    
    ctrlShiftF7();
    assertRangeText("i");
  }

  public void testStaticallyImportedOverloadsFromUsage() throws IOException {
    createClass("""

                  class Foo {
                    static void foo(int a) {}
                    static void foo(int a, int b) {}
                  }""");
    configureByText(JavaFileType.INSTANCE, """

      import static Foo.foo;

      class Bar {
        {
          <caret>foo(1);
        }
      }""");
    ctrlShiftF7();
    assertRangeText("foo", "foo");
  }

  public void testStaticallyImportedOverloadsFromImport() throws IOException {
    createClass("""

                  class Foo {
                    static void foo(int a) {}
                    static void foo(int a, int b) {}
                  }""");
    configureByText(JavaFileType.INSTANCE, """

      import static Foo.<caret>foo;

      class Bar {
        {
          foo(1);
        }
      }""");
    ctrlShiftF7();
    assertRangeText("foo", "foo", "foo"); //import highlighted twice: for each overloaded usage target
  }

  public void testIdentifierHighlighterForStaticImports() {
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), ()->{
      try {
        createClass("""

                      class Foo {
                        static void foo(int a) {}
                        static void foo(int a, int b) {}
                      }""");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      configureByText(JavaFileType.INSTANCE, """

        import static Foo.fo<caret>o;

        class Bar {
          {
            foo(1);
          }
        }""");

      IdentifierHighlighterPassFactory.waitForIdentifierHighlighting();

      assertEquals(2, getIdentifierHighlighters()
        .stream()
        .filter(info -> getFile().getText().substring(info.startOffset, info.endOffset).equals("foo"))
        .count());
    });
  }

  @NotNull
  List<HighlightInfo> getIdentifierHighlighters() {
    return Arrays.stream(myEditor.getMarkupModel().getAllHighlighters())
      .map(rh -> HighlightInfo.fromRangeHighlighter(rh))
      .filter(Objects::nonNull)
      .filter(info -> info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY)
      .collect(Collectors.toList());
  }
  public void testExceptionsInTryWithResources() {
    configureByText(JavaFileType.INSTANCE, """

      import java.io.*;
      class A {
        public void test() throws IOException {
          try (InputStream in = new FileInputStream("file.name")) { }
          <caret>catch (FileNotFoundException e) { throw new FileNotFoundException("no highlighting here"); }
        }
      }""");
    ctrlShiftF7();
    assertRangeText("FileInputStream", "catch");
  }

  public void testExceptionsResourceCloser() {
    configureByText(JavaFileType.INSTANCE, """

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

  public void testMethodParameterEndOfIdentifier() {
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), ()-> {
      try {
        configureFile();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      doHighlighting();
      Segment[] segments = getIdentifierHighlighters().toArray(Segment.EMPTY_ARRAY);
      assertSegments(segments, "28:33 param", "60:65 param", "68:73 param");
    });
  }

  public void testRecordComponents() {
    configureByText(JavaFileType.INSTANCE, """

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

  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_16; // records are needed
  }

  public void testCompactConstructorParameters() {
    configureByText(JavaFileType.INSTANCE, """

      record A(String s) {
        A {
          <caret>s;
        }
      }""");
    ctrlShiftF7();
    assertRangesAndTexts("17:18 s", "32:33 s");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    //myFixture.setReadEditorMarkupModel(true);
  }

  /**
   * @param expected sorted by startOffset
   */
  private void assertRangesAndTexts(String... expected) {
    RangeHighlighter[] highlighters = getEditor().getMarkupModel().getAllHighlighters();
    assertSegments(highlighters, expected);
  }

  private void assertSegments(Segment[] highlighters, String... expected) {
    String[] actual = Arrays.stream(highlighters)
      .sorted(Comparator.comparingInt(Segment::getStartOffset))
      .map(it -> it.getStartOffset() +
                 ":" +
                 it.getEndOffset() +
                 " " +
                 getFile().getText().substring(it.getStartOffset(), it.getEndOffset()))
      .toArray(String[]::new);
    assertSameElements(actual, expected);
  }

  private void configureFile() throws Exception {
    String testName = getTestName(false);
    configureByFile("/codeInsight/highlightUsagesHandler/" + testName + ".java");
    //copy(getTestDataPath()+"/codeInsight/highlightUsagesHandler/" + testName + ".java", configureByFile(), testName + ".java")
    //VirtualFile file = myFixture.copyFileToProject("/codeInsight/highlightUsagesHandler/" + testName + ".java", testName + ".java");
    //configureByExistingFile(file);
  }

  private void ctrlShiftF7() {
    HighlightUsagesHandler.invoke(getProject(), getEditor(), getFile());
  }

  private void assertRangeText(String... texts) {
    List<RangeHighlighter> highlighters = ContainerUtil.filter(getEditor().getMarkupModel().getAllHighlighters(), it -> it.getLayer() == HighlightManagerImpl.OCCURRENCE_LAYER);
    List<String> actual = ContainerUtil.map(highlighters, it -> getFile().getText().substring(it.getStartOffset(), it.getEndOffset()));
    assertSameElements(actual, texts);
  }

  private void checkUnselect() {
    ctrlShiftF7();
    assertRangeText();
  }

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
    configureByText( JavaFileType.INSTANCE, s);

    HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(getEditor(), getFile());
    assertNotNull(handler);
    List<PsiElement> targets = handler.getTargets();
    assertEquals(1, targets.size());

    handler.computeUsages(targets);
    List<TextRange> readUsages = handler.getReadUsages();
    List<String> expected = Arrays.asList("IOException", "file.createNewFile", "file.getCanonicalPath");
    assertEquals(expected.size(), readUsages.size());

    List<String> textUsages = ContainerUtil.map(readUsages, it->getFile().getText().substring(it.getStartOffset(), it.getEndOffset()));
    assertSameElements(expected, textUsages);
  }
}