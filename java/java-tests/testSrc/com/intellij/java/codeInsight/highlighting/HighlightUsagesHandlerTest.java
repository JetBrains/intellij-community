// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    configureByText( JavaFileType.INSTANCE, "\n"+
      "class Foo {\n"+
      "  public String foo(String s) {\n"+
      "    while (s.length() > 0) {\n"+
      "      if (s.length() < 0) {\n"+
      "        s = \"\";\n"+
      "        continue;\n"+
      "      }\n"+
      "      else {\n"+
      "      }\n"+
      "    }\n"+
      "    <caret>return s;\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangeText("return s;");
  }

  public void testReturnsInTryFinally() {
    // See IDEADEV-14028
    configureByText( JavaFileType.INSTANCE, "\n"+
      "class Foo {\n"+
      "  int foo(boolean b) {\n"+
      "    try {\n"+
      "      if (b) return 1;\n"+
      "    }\n"+
      "    finally {\n"+
      "      if (b) return 2;\n"+
      "    }\n"+
      "    <caret>return 3;\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangeText("return 1;", "return 2;", "return 3;");
  }

  public void testReturnsInLambda() {
    // See IDEADEV-14028
    configureByText( JavaFileType.INSTANCE, "\n"+
      "class Foo {\n"+
      "  {\n"+
      "    Runnable r = () -> {\n"+
      "           if (true) return;\n"+
      "           <caret>return;\n"+
      "    }\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangeText("return;", "return;");
  }

  public void testSuppressedWarningsHighlights() {
    configureByText( JavaFileType.INSTANCE, "\n"+
      "class Foo {\n"+
      "  @SuppressWarnings({\"Sil<caret>lyAssignment\"});\n"+
      "  void foo() {\n"+
      "      int i = 0;\n"+
      "      i = i;\n"+
      "  }\n"+
      "}");
    enableInspectionTool(new SillyAssignmentInspection());
    ctrlShiftF7();
    assertRangeText("i");
  }

  public void testSuppressedWarningsInInjectionHighlights() {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());

    @Language("JAVA")
    String text = "\n"+
      "class Foo {\n"+
      "  public static void a(boolean b, String c) {\n"+
      "     @SuppressWarnings(\"SillyAssignment\")\n"+
      "     String java = \"class A {{int i = 0; i = i;}}\";\n"+
      "  }\n"+
      "}";
    configureByText( JavaFileType.INSTANCE, text);
    enableInspectionTool(new SillyAssignmentInspection());
    getEditor().getCaretModel().moveToOffset(getFile().getText().indexOf("illyAssignment"));
    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
    
    ctrlShiftF7();
    assertRangeText("i");
  }

  public void testStaticallyImportedOverloadsFromUsage() throws IOException {
    createClass("\n"+
      "class Foo {\n"+
      "  static void foo(int a) {}\n"+
      "  static void foo(int a, int b) {}\n"+
      "}");
    configureByText( JavaFileType.INSTANCE, "\n"+
      "import static Foo.foo;\n"+
      "\n"+
      "class Bar {\n"+
      "  {\n"+
      "    <caret>foo(1);\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangeText("foo", "foo");
  }

  public void testStaticallyImportedOverloadsFromImport() throws IOException {
    createClass("\n"+
      "class Foo {\n"+
      "  static void foo(int a) {}\n"+
      "  static void foo(int a, int b) {}\n"+
      "}");
    configureByText( JavaFileType.INSTANCE, "\n"+
      "import static Foo.<caret>foo;\n"+
      "\n"+
      "class Bar {\n"+
      "  {\n"+
      "    foo(1);\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangeText("foo", "foo", "foo"); //import highlighted twice: for each overloaded usage target
  }

  public void testIdentifierHighlighterForStaticImports() {
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), getTestRootDisposable(), ()->{
      try {
        createClass("\n"+
        "class Foo {\n"+
        "  static void foo(int a) {}\n"+
        "  static void foo(int a, int b) {}\n"+
        "}");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      configureByText(JavaFileType.INSTANCE, "\n"+
      "import static Foo.fo<caret>o;\n"+
      "\n"+
      "class Bar {\n"+
      "  {\n"+
      "    foo(1);\n"+
      "  }\n"+
      "}");

      IdentifierHighlighterPassFactory.waitForIdentifierHighlighting();
      //import highlighted twice: for each overloaded usage target
      assertEquals(3, getIdentifierHighlighters()
        .stream()
        .filter(info -> getFile().getText().substring(info.startOffset, info.endOffset).equals("foo"))
        .count());
    });
  }

  @NotNull
  List<HighlightInfo> getIdentifierHighlighters() {
    return Arrays.stream(myEditor.getMarkupModel().getAllHighlighters())
      .map(rh -> (HighlightInfo)rh.getErrorStripeTooltip())
      .filter(Objects::nonNull)
      .filter(info -> info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY)
      .collect(Collectors.toList());
  }
  public void testExceptionsInTryWithResources() {
    configureByText( JavaFileType.INSTANCE, "\n"+
      "import java.io.*;\n"+
      "class A {\n"+
      "  public void test() throws IOException {\n"+
      "    try (InputStream in = new FileInputStream(\"file.name\")) { }\n"+
      "    <caret>catch (FileNotFoundException e) { throw new FileNotFoundException(\"no highlighting here\"); }\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangeText("FileInputStream", "catch");
  }

  public void testExceptionsResourceCloser() {
    configureByText( JavaFileType.INSTANCE, "\n"+
      "import java.io.*;\n"+
      "class A {\n"+
      "  public void test() {\n"+
      "    try (InputStream in = new FileInputStream(\"file.name\")) { }\n"+
      "    <caret>catch (IOException e) { }\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangeText("in", "FileInputStream", "FileInputStream", "catch");
  }

  public void testMethodParameterEndOfIdentifier() {
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), getTestRootDisposable(), ()-> {
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
    configureByText( JavaFileType.INSTANCE, "\n"+
      "record A(String s) {\n"+
      "  void test() {\n"+
      "    <caret>s();\n"+
      "    s();\n"+
      "    String a = s;\n"+
      "  }\n"+
      "}");
    ctrlShiftF7();
    assertRangesAndTexts("17:18 s", "42:43 s", "51:52 s", "71:72 s");
  }

  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_16; // records are needed
  }

  public void testCompactConstructorParameters() {
    configureByText(JavaFileType.INSTANCE, "\n"+
      "record A(String s) {\n"+
      "  A {\n"+
      "    <caret>s;\n"+
      "  }\n"+
      "}");
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
    String s = "\n"+
      "import java.io.*;\n"+
      "class A {\n"+
      "  public static void deserialize(File file) throws <caret>IOException, java.lang.RuntimeException, ClassNotFoundException {\n"+
      "    boolean length = file.createNewFile();\n"+
      "    if (length == false) throw new RuntimeException();\n"+
      "    file.getCanonicalPath();\n"+
      "    if (length == true) throw new ClassNotFoundException();\n"+
      "  }\n"+
      "}";
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