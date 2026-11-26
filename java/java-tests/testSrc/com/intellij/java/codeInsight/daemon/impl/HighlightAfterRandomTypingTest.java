// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightInfoUpdater;
import com.intellij.codeInsight.daemon.impl.HighlightInfoUpdaterImpl;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInsight.highlighting.HyperlinkAnnotator;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SkipSlowTestLocally
@TestApplication
public class HighlightAfterRandomTypingTest {
  private static final Logger LOG = Logger.getInstance(HighlightAfterRandomTypingTest.class);

  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk21()));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, () -> List.of(), LightDaemonAnalyzerOptions.DEFAULT.withProductionDaemon());

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    myFixture.enableInspections(configureLocalInspectionTools(myFixture.getTestName(false)));
    if ("RandomEditingForUnused".equals(myFixture.getTestName(false))) {
      myFixture.enableInspections(new UnusedDeclarationInspection());
      myFixture.enableInspections(new UnusedImportInspection());
    }
    // preload extensions to avoid "PSI/document/model changes are not allowed during highlighting"
    EntryPointsManagerBase.getInstance(myFixture.getProject()).getAdditionalAnnotations();
  }

  private static final Set<String> ignoredTools = Set.of(
    "IncorrectFormatting",
    "GrazieInspection", "GrazieInspectionRunner", "GrazieStyle","SpellCheckingInspection", // avoid warnings inside comments after random typing
    "CodeBlock2Expr"/* sensitive to comments*/
  );

  private static LocalInspectionTool @NotNull [] configureLocalInspectionTools(@NotNull String testName) {
    if ("RandomEditingForUnused".equals(testName)) {
      return LocalInspectionTool.EMPTY_ARRAY;
    }
    List<InspectionToolWrapper<?, ?>> all = InspectionToolRegistrar.getInstance().createTools();
    List<LocalInspectionTool> locals = new ArrayList<>();
    for (InspectionToolWrapper<?,?> tool : all) {
      if (ignoredTools.contains(tool.getShortName())) {
        continue;
      }
      if (tool instanceof LocalInspectionToolWrapper) {
        LocalInspectionTool e = ((LocalInspectionToolWrapper)tool).getTool();
        locals.add(e);
      }
    }
    return locals.toArray(LocalInspectionTool.EMPTY_ARRAY);
  }

  @SuppressWarnings("ALL") @Language("JAVA")
  private static final String classText =
"""
class X {
  void f ( ) {
    List < String > ls = new ArrayList < String > ( 1 ) ; ls . toString ( ) ;
    List < Integer > is = new ArrayList < Integer > ( 1 ) ; is . toString ( ) ;
    List i = new ArrayList ( 1 ) ; i . toString ( ) ;
    Collection < Number > l2 = new ArrayList < Number > ( 10 ) ; l2 . toString ( ) ;
    Collection < Number > l22 = new ArrayList < Number > ( ) ; l22 . toString ( ) ;
    Map < Number , String > l3 = new HashMap < Number , String > ( 10 ) ; l3 . toString ( ) ;
    Map < String , String > m = new HashMap < String , String > ( ) ; m . toString ( ) ;
    Map < String , String > m1 = new HashMap < String , String > ( ) ; m1 . toString ( ) ;
    Map < String , String > m2 = new HashMap < String , String > ( ) ; m2 . toString ( ) ;
    Map < String , String > m3 = new HashMap < String , String > ( ) ; m3 . toString ( ) ;
    Map < String , String > mi = new HashMap < String , String > ( 1 ) ; mi . toString ( ) ;
    Map < String , String > mi1 = new HashMap < String , String > ( 1 ) ; mi1 . toString ( ) ;
    Map < String , String > mi2 = new HashMap < String , String > ( 1 ) ; mi2 . toString ( ) ;
    Map < String , String > mi3 = new HashMap < String , String > ( 1 ) ; mi3 . toString ( ) ;
    Map < Number , String > l4 = new HashMap < Number , String > ( ) ; l4 . toString ( ) ;
    Map < Number , String > l5 = new HashMap < Number , String > ( l4 ) ; l5 . toString ( ) ;
    HashMap < Number , String > l6 = new HashMap < Number , String > ( ) ; l6 . toString ( ) ;
    Map < List < Integer > , Map < String , List < String > > > l7 = new HashMap ( 1 ) ; l7 . toString ( ) ;
    java . util . Map < java . util . List < Integer > ,
                        java . util . Map < String , java . util . List < String > > > l77 =
    new java . util . HashMap ( 1 ) ; l77 . toString ( ) ;
    l77 . computeIfPresent ( Collections . emptyList ( ) , ( oldKey , oldValue ) -> {
      if ( oldKey . equals ( this ) ) {
        return l77 . compute ( oldKey . stream ( ) .
                                        filter ( i2 -> i2 . toString ( ) . equals ( this ) ) .
                                        toList ( ),
                           ( integers , map ) -> {
                             return oldValue ;
                           } );
      }
      else {
        return oldKey == null ? Collections . emptyMap ( ) :
               Collections . synchronizedMap ( Collections . emptyMap ( ) );
      }
    } );
  }
}
""";

  @SuppressWarnings("All") @Language("JAVA")
  @NonNls private static final String text =
    "import java.util.*;\n" + classText;

  @Test
  public void testAllTheseConcurrentThreadsDoNotCrashAnything() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    Project project = daemon.getFixture().getProject();
    daemon.runTest(() -> {
      long time = System.currentTimeMillis();
      for (int i = 0; i < 20/*00000*/; i++) {
        //System.out.println("i = " + i);
        EdtTestUtil.runInEdtAndWait(() -> PsiManagerEx.getInstanceEx(project).cleanupForNextTest());

        daemon.configureFromFileText("A.java", text);
        List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.ERROR);
        assertEmpty(infos);
        EdtTestUtil.runInEdtAndWait(() -> {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          FileEditorManagerEx.getInstanceEx(project).closeAllFiles();
        });
      }
      LOG.debug(System.currentTimeMillis() - time+"ms");
    });
  }

  @Test
  public void testThatInsertingJavadocCommentsInRandomWhitespaceDoesNotCrashAnything() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    Project project = myFixture.getProject();
    daemon.runTest(() -> {
      daemon.configureFromFileText("A.java", text);
      List<HighlightInfo> oldWarnings = new ArrayList<>(daemon.waitHighlighting(HighlightSeverity.WARNING));
      Comparator<HighlightInfo> infoComparator = (o1, o2) -> {
        if (o1.equals(o2)) return 0;
        if (o1.getActualStartOffset() != o2.getActualStartOffset()) return o1.getActualStartOffset() - o2.getActualStartOffset();
        return text(o1).compareTo(text(o2));
      };
      oldWarnings.sort(infoComparator);
      List<String> oldWarningTexts = new ArrayList<>();
      for (HighlightInfo info : oldWarnings) {
        oldWarningTexts.add(text(info));
        assert info.getHighlighter().isValid();
      }

      long seed =//2515594662124237646L;
      new Random().nextLong();
      Random random = new Random(seed);

      DaemonCodeAnalyzerEx.getInstanceEx(project).restart(myFixture.getTestName(false));
      int N = 20;
      long[] time = new long[N];

      int oldWarningSize = oldWarnings.size();
      // check that inserting /*--*/ inside whitespace does not crash anything
      for (int i = 0; i < N; i++) {
        EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
        long start = System.currentTimeMillis();

        LOG.debug("i = " + i);
        String s = ReadAction.computeBlocking(() -> myFixture.getFile().getText());
        int offset;
        do {
          offset = random.nextInt(s.length());
        }
        while (s.charAt(offset) != ' ');
        moveCaret(myFixture, offset);
        daemon.type("/*--*/");
        List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.WARNING);
        if (oldWarningSize != infos.size()) {
          infos = new ArrayList<>(infos);
          infos.sort(infoComparator);

          for (int k=0; k<Math.min(infos.size(), oldWarningSize);k++) {
            HighlightInfo info = infos.get(k);
            String text = text(info);
            String oldText = oldWarningTexts.get(k);
            if (!text.equals(oldText)) {
              System.err.println(k+"\n"+
                                 "Old: "+oldText+"; info: " + oldWarnings.get(k)+";\n" +
                                 "New: "+text+   "; info: " + info);
              break;
            }
          }
          String oldText = StringUtil.join(oldWarningTexts, "\n");
          String newText = StringUtil.join(infos, info->text(info), "\n");
          if (!oldText.equals(newText)) {
            daemon.waitHighlighting(HighlightSeverity.WARNING);
          }
          assertEquals(oldText, newText, seed+"");
          assertEquals(oldWarningSize, infos.size(), infos.toString());
        }
        for (HighlightInfo info : infos) {
          assertNotSame(HighlightSeverity.ERROR, info.getSeverity(), String.valueOf(info));
        }
        for (int k=0; k<"/*--*/".length();k++) {
          daemon.backspace();
        }
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

        long end = System.currentTimeMillis();
        time[i] = end - start;
      }
      EdtTestUtil.runInEdtAndWait(() -> FileEditorManagerEx.getInstanceEx(project).closeAllFiles());

      LOG.debug("Average among the N/3 median times: " + ArrayUtil.averageAmongMedians(time, 3) + "ms");
    });
  }

  @NotNull
  private static String text(@NotNull HighlightInfo info) {
    return TextRange.create(info)+": '"+info.getText()+"': '" + info.getDescription()+"' "+info.getSeverity();
  }

  private static void moveCaret(@NotNull LightJavaCodeInsightTestFixture myFixture, int offset) {
    Editor editor = myFixture.getEditor();
    EdtTestUtil.runInEdtAndWait(() -> editor.getCaretModel().moveToOffset(offset));
  }

  private static void assertEmpty(@NotNull Collection<?> collection) {
    assertTrue(collection.isEmpty(), collection.toString());
  }

  private static void assertNotEmpty(@NotNull Collection<?> collection) {
    assertFalse(collection.isEmpty());
  }

  @Test
  public void testRandomEditingForUnused() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    Project project = myFixture.getProject();
    daemon.runTest(() -> {
      daemon.configureFromFileText("A.java", "class X {<caret>}");

      final StringBuilder imports = new StringBuilder();
      final StringBuilder usages = new StringBuilder();
      ReadAction.runBlocking(() -> {
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        String[] names = cache.getAllClassNames();

        int v = 0;
        outer:
        for (String name : names) {
          PsiClass[] classes = cache.getClassesByName(name, GlobalSearchScope.allScope(project));
          if (classes.length == 0) continue;
          PsiClass aClass = classes[0];
          if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) continue;
          if (aClass.getSuperClass() == null) continue;
          PsiClassType[] superTypes = aClass.getSuperTypes();
          if (superTypes.length == 0) continue;
          for (PsiClassType superType : superTypes) {
            PsiClass superClass = superType.resolve();
            if (superClass == null || !superClass.hasModifierProperty(PsiModifier.PUBLIC)) continue outer;
          }
          String qualifiedName = aClass.getQualifiedName();
          if (qualifiedName.startsWith("java.lang.invoke")) continue ; // java.lang.invoke.MethodHandle has weird access attributes in recent rt.jar which causes spurious highlighting errors
          if (!accessible(aClass, new HashSet<>())) continue;
          imports.append("import " + qualifiedName + ";\n");
          usages.append("/**/ "+aClass.getName() + " var" + v + " = null; var" + v + ".toString();\n");
          v++;
          if (v>100) break;
        }
      });
      final String text = imports + "\n class X {{\n" + usages + "}}";
      Editor editor = myFixture.getEditor();
      WriteCommandAction.runWriteCommandAction(null, () -> editor.getDocument().setText(text));

      List<HighlightInfo> errors = daemon.waitHighlighting(HighlightSeverity.WARNING);
      assertTrue(errors.isEmpty(), text + "\n" + errors);
      Random random = new Random();
      int unused = 0;
      for (int i = 0; i < 100; i++) {
        String s = ReadAction.computeBlocking(() -> myFixture.getFile().getText());

        int offset;
        do {
          offset = random.nextInt(s.length());
        }
        while (!CharArrayUtil.regionMatches(s, offset, "/**/") && !CharArrayUtil.regionMatches(s, offset, "//"));

        char next = offset < s.length()-1 ? s.charAt(offset+1) : 0;
        if (next == '/') {
          moveCaret(myFixture, offset + 1);
          daemon.type("**");
          unused--;
        }
        else if (next == '*') {
          moveCaret(myFixture, offset + 1);
          daemon.delete();
          daemon.delete();
          unused++;
        }
        else {
          continue;
        }
        EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
        ReadAction.runBlocking(() -> myFixture.getFile().accept(new PsiRecursiveElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            assertTrue(element.isValid(), element.toString());
            super.visitElement(element);
          }
        }));

        //System.out.println("i = " + i + " " + next + " at "+offset);

        List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.WARNING);
        errors = DaemonAnalyzerFixture.filter(infos, HighlightSeverity.ERROR);
        assertEmpty(errors);
        List<HighlightInfo> warns = DaemonAnalyzerFixture.filter(infos, HighlightSeverity.WARNING);
        if (unused != warns.size()) {
          assertEquals(unused, warns.size(), warns.toString());
        }
      }
      EdtTestUtil.runInEdtAndWait(() -> FileEditorManagerEx.getInstanceEx(project).closeAllFiles());
    });
  }

  private static boolean accessible(PsiClass aClass, Set<? super PsiClass> visited) {
    if (!visited.add(aClass)) return false;
    // this class and all its super- and containing- classes should be public
    if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    for (PsiClass superClass : aClass.getSupers()) {
      if (!accessible(superClass, visited)) return false;
    }
    PsiClass containingClass = aClass.getContainingClass();
    return containingClass == null || accessible(containingClass, visited);
  }

  @Test
  public void testRandomTypingAndDeletingCommentAndCheckingNoHighlightsAreLeftInside() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    Project project = myFixture.getProject();
    daemon.runTest(() -> {
      disableHyperlinkAnnotator();
      long seed =
      new Random().nextLong();
      StringBuilder t = new StringBuilder(text);
      Random random = new Random(seed);
      // generate some errors
      for (int i=0;i<20;i++) {
        int offset = random.nextInt(text.length());
        t.insert(offset, '-');
      }
      daemon.configureFromFileText("A.java", t.toString());

      List<HighlightInfo> initErrors = ContainerUtil.sorted(daemon.waitHighlighting(HighlightSeverity.ERROR), HighlightInfoUpdaterImpl.BY_OFFSETS_AND_HASH_ERRORS_FIRST);

      int N = 100;

      // check that inserting /*--*/ inside whitespace does not crash anything
      for (int i = 0; i < N; i++) {
        try {
          EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());

          LOG.debug("i = " + i);
          String s = ReadAction.computeBlocking(() -> myFixture.getFile().getText());
          int offset = random.nextInt(20/*do not mess with * in imports*/,s.length()-5);
          moveCaret(myFixture, offset);
          daemon.type("/*-");
          List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.ERROR);
          assertNotEmpty(infos);

          List<HighlightInfo> collect = new ArrayList<>();
          Document document = myFixture.getEditor().getDocument();
          ReadAction.runBlocking(() -> {
            PsiElement comment = SyntaxTraverser.psiTraverser(myFixture.getFile()).filter(e -> e instanceof PsiComment && e.getText().startsWith("/*-")).toList().getFirst();

            TextRange cRange = comment.getTextRange().cutOut(TextRange.create(4, comment.getTextRange().getLength()-1)); // to strictly contain
            //noinspection ConstantValue
            DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, cRange.getStartOffset(), cRange.getEndOffset(),
                                                   h -> !cRange.contains(h) || !collect.add(h));
          });
          assertTrue(collect.isEmpty(), "Found error inside comment: "+collect+"; seed="+seed);

          daemon.backspace();
          daemon.backspace();
          daemon.backspace();
          EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

          List<HighlightInfo> after = ContainerUtil.sorted(daemon.waitHighlighting(HighlightSeverity.ERROR), HighlightInfoUpdaterImpl.BY_OFFSETS_AND_HASH_ERRORS_FIRST);
          assertEquals(initErrors, after, "highlighters didn't restore; seed="+seed);
        }
        catch (Throwable e) {
          System.err.println("Error running with seed "+seed);
          throw e;
        }
      }
    });
  }

  private static String genRandomChar(Random random) {
    int leftLimit = 48; // numeral '0'
    int rightLimit = 122; // letter 'z'

    return Character.toString(random.nextInt(leftLimit, rightLimit + 1));
  }

  private int withTypedLetter(int num, String s, Random random, Runnable in) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Editor editor = myFixture.getEditor();
    int length = editor.getDocument().getTextLength();
    int errOffset = random.nextInt(20/*do not mess with * in imports*/,length-5);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), ()->editor.getDocument().insertString(errOffset, s)); // do not call type() because of overtyping risks
    try {
      in.run();
    }
    finally {
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), ()-> {
        editor.getDocument().deleteString(errOffset, errOffset+s.length());
      }); // do not call type() because of overtyping risks
    }
    return errOffset;
  }
  private @NotNull List<HighlightInfo> doHighlightAndSort(long seed) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.WARNING);
    List<HighlightInfo> sorted = ContainerUtil.sorted(infos, Comparator.comparing(h->text(h)));
    for (HighlightInfo info : sorted) {
      assertTrue(info.getHighlighter().isValid());
    }
    Editor editor = daemon.getFixture().getEditor();
    Project project = daemon.getFixture().getProject();
    ReadAction.runBlocking(() -> assertNoDuplicateHighlighters(DocumentMarkupModel.forDocument(editor.getDocument(), project, true), editor, seed));
    return sorted;
  }

  @Test
  public void testCheckThat8812599550819766708LTypingDoesNotCreateDuplicateRangeHighlighters() {
    long seed =8812599550819766708L;
    Random random = new Random(seed);
    daemonFixture.get().runTest(() -> randomTypeAndCheckForDuplicates(seed, random));
  }
  @Test
  public void testCheckThatRandomTypingDoesNotCreateDuplicateRangeHighlighters() {
    long seed =//-1816450852840074871L;
    new Random().nextLong();
    Random random = new Random(seed);
    daemonFixture.get().runTest(() -> randomTypeAndCheckForDuplicates(seed, random));
  }

  private void randomTypeAndCheckForDuplicates(long seed, Random random) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    HighlightInfoUpdaterImpl updater = (HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(daemon.getFixture().getProject());
    updater.runAssertingInvariants(() -> {
      disableHyperlinkAnnotator();
      daemon.configureFromFileText("A.java", "import java.util.*;\n" + classText.repeat(1/*0*/));
      List<HighlightInfo> initErrors = doHighlightAndSort(seed);
      assertNotEmpty(initErrors);

      int N = 50;

      try {
        typeAndCheck(seed, N, random, initErrors);
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
      }
      catch (Throwable e) {
        System.err.println("Error running with seed " + seed + "L");
        throw e;
      }
    });
  }

  private void disableHyperlinkAnnotator() {
    // hyperlink annotator might find spurious stuff inside comment
    ExtensionPointImpl<KeyedLazyInstance<Annotator>> point = (ExtensionPointImpl<KeyedLazyInstance<Annotator>>)LanguageAnnotators.INSTANCE.getPoint();
    List<KeyedLazyInstance<Annotator>> all = point.getExtensionList();
    List<KeyedLazyInstance<Annotator>> exceptHyper = ContainerUtil.filter(all, a -> !(a instanceof LanguageExtensionPoint<?> l && HyperlinkAnnotator.class.getName().equals(l.implementationClass)));
    point.maskAll(exceptHyper, daemonFixture.get().getFixture().getTestRootDisposable(), false);
  }

  private static void assertNoDuplicateHighlighters(MarkupModel markupModel, Editor editor, long seed) {
    record HI(TextRange range, String desc, TextAttributes attributes){}
    Set<HI> set = new HashSet<>();
    List<RangeHighlighter> highlighters = new ArrayList<>();
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
      if (info == null
          || info.getToolId() == HighlightVisitorImpl.class && TextRange.create(info).isEmpty()
          || info.getToolId() == HighlightVisitorImpl.class && TextRange.create(info).getLength()==1 && info.getDescription() != null && StringUtil.endsWith(info.getDescription(), " expected")
        || !highlighter.isValid()
      ) { // ignore empty error elements, there are can be multiple at the same offset after reparse
        continue;
      }
      highlighters.add(highlighter);
      HI hi = new HI(highlighter.getTextRange(), info.getDescription(), info.getTextAttributes(null, editor.getColorsScheme()));
      if (!set.add(hi)) {
        fail("Duplicate found: \n" + highlighter + "; seed=" + seed+"\n"+highlighters);
      }
    }
  }

  private @NotNull String typeAndCheck(long seed, int num, Random random, List<? extends HighlightInfo> initInfos) {
    //System.out.println("num = " + num);
    String initInfoText = StringUtil.join(initInfos, h->text(h), "\n");
    if (num == 0) return initInfoText;
    String s = random.nextBoolean() ? genRandomChar(random) : "/*";
    int offset = withTypedLetter(num, s, random, () -> {
      List<HighlightInfo> infos = doHighlightAndSort(seed);
      String after = typeAndCheck(seed, num - 1, random, infos);
    });
    
    List<HighlightInfo> after = doHighlightAndSort(seed);
    String afterText = StringUtil.join(after, h -> text(h), "\n");
    if (!initInfoText.equals(afterText)) {
      List<HighlightInfo> a3 = doHighlightAndSort(seed);
      String actualInfoText = StringUtil.join(a3, h -> text(h), "\n");
      assertEquals(initInfoText, actualInfoText, "highlighters didn't restore; seed=" + seed+"; num="+num);
    }
    return afterText;
  }

  @Test
  public void testCheckThatTypingRandomErrorsAndThenOpenJavadocCommentDoesGenerateGiantCommentedTextButDoesNotLeaveErrorsInsideCommentedOutText() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    Project project = myFixture.getProject();
    daemon.runTest(() -> {
      disableHyperlinkAnnotator();
      long seed = //2518799306412815874L;
      new Random().nextLong();
      Random random = new Random(seed);
      daemon.configureFromFileText("A.java", "import java.util.*;\n" + classText.repeat(5));

      List<HighlightInfo> initErrors = doHighlightAndSort(seed);
      assertNotEmpty(initErrors);

      int N = 10;

      // check that inserting /*--*/ inside whitespace does not crash anything
      for (int i = 0; i < N; i++) {
        //System.out.println("i = " + i);
        try {
          // generate some errors
          withTypedLetter(0, genRandomChar(random), random, () -> {
            withTypedLetter(0, genRandomChar(random), random, () -> {
              withTypedLetter(0, "/*", random, () -> {
                List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.ERROR);
                assertNotEmpty(infos);

                List<HighlightInfo> collect = new ArrayList<>();
                Document document = myFixture.getEditor().getDocument();
                ReadAction.runBlocking(() -> {
                  PsiElement comment = SyntaxTraverser.psiTraverser(myFixture.getFile()).filter(e -> e instanceof PsiComment && e.getText().startsWith("/*")).toList().getFirst();

                  TextRange cringe =
                    comment.getTextRange().cutOut(TextRange.create(4, comment.getTextRange().getLength() - 1)); // to strictly contain
                  //noinspection ConstantValue
                  DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightInfoType.SYMBOL_TYPE_SEVERITY,
                                                         cringe.getStartOffset(), cringe.getEndOffset(),
                                                         h -> !(cringe.contains(h) && collect.add(h)));
                });
                assertTrue(collect.isEmpty(), "Found stray highlighting inside comment: " + collect + "; seed=" + seed);
              });
            });
          });

          EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

          List<HighlightInfo> after = doHighlightAndSort(seed);
          assertEquals(initErrors, after, "highlighters didn't restore; seed="+seed);
        }
        catch (Throwable e) {
          System.err.println("Error running with seed "+seed+"L");
          throw e;
        }
      }
    });
  }
}
