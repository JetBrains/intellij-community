// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.daemon;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.ProductionLightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.FileStatusMap;
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.ArrayUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@SkipSlowTestLocally
public class HighlightAfterRandomTypingTest extends ProductionLightDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if ("RandomEditingForUnused".equals(getTestName(false))) {
      enableInspectionTool(new UnusedDeclarationInspection());
      enableInspectionTool(new UnusedImportInspection());
    }
    // preload extensions to avoid "PSI/document/model changes are not allowed during highlighting"
    EntryPointsManagerBase.getInstance(getProject()).getAdditionalAnnotations();
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk21();
  }

  private static final Set<String> ignoredTools = Set.of(
    "IncorrectFormatting",
    "GrazieInspection", "GrazieInspectionRunner", "GrazieStyle","SpellCheckingInspection", // avoid warnings inside comments after random typing
    "CodeBlock2Expr"/* sensitive to comments*/
  );

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    if ("RandomEditingForUnused".equals(getTestName(false))) {
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

  public void testAllTheseConcurrentThreadsDoNotCrashAnything() {
    long time = System.currentTimeMillis();
    for (int i = 0; i < 20/*00000*/; i++) {
      //System.out.println("i = " + i);
      ((PsiManagerEx)getPsiManager()).cleanupForNextTest();

      configureFromFileText("A.java", text);
      List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      assertEmpty(infos);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
    }
    LOG.debug(System.currentTimeMillis() - time+"ms");
  }

  public void testThatInsertingJavadocCommentsInRandomWhitespaceDoesNotCrashAnything() {
    configureFromFileText("A.java", text);
    List<HighlightInfo> oldWarnings = new ArrayList<>(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING));
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

    DaemonCodeAnalyzerEx.getInstanceEx(getProject()).restart(getTestName(false));
    int N = 20;
    long[] time = new long[N];

    int oldWarningSize = oldWarnings.size();
    // check that inserting /*--*/ inside whitespace does not crash anything
    for (int i = 0; i < N; i++) {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      long start = System.currentTimeMillis();

      LOG.debug("i = " + i);
      String s = getFile().getText();
      int offset;
      while (true) {
        offset = random.nextInt(s.length());
        if (s.charAt(offset) == ' ') {
          break;
        }
      }
      getEditor().getCaretModel().moveToOffset(offset);
      type("/*--*/");
      List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
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
          List<HighlightInfo> infos1 = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
          int size1 = infos1.size();
        }
        assertEquals(seed+"",oldText, newText);
        assertEquals(infos.toString(), oldWarningSize, infos.size());
      }
      for (HighlightInfo info : infos) {
        assertNotSame(String.valueOf(info), HighlightSeverity.ERROR, info.getSeverity());
      }
      for (int k=0; k<"/*--*/".length();k++) {
        backspace();
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      long end = System.currentTimeMillis();
      time[i] = end - start;
    }
    FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();

    LOG.debug("Average among the N/3 median times: " + ArrayUtil.averageAmongMedians(time, 3) + "ms");
  }

  @NotNull
  private static String text(@NotNull HighlightInfo info) {
    return TextRange.create(info)+": '"+info.getText()+"': '" + info.getDescription()+"' "+info.getSeverity();
  }

  public void testRandomEditingForUnused() {
    configureFromFileText("A.java", "class X {<caret>}");

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(getProject());
    String[] names = cache.getAllClassNames();

    final StringBuilder imports = new StringBuilder();
    final StringBuilder usages = new StringBuilder();
    int v = 0;
    outer:
    for (String name : names) {
      PsiClass[] classes = cache.getClassesByName(name, GlobalSearchScope.allScope(getProject()));
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
    final String text = imports + "\n class X {{\n" + usages + "}}";
    WriteCommandAction.runWriteCommandAction(null, () -> getEditor().getDocument().setText(text));

    List<HighlightInfo> errors = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertEmpty(text, errors);
    Random random = new Random();
    int unused = 0;
    for (int i = 0; i < 100; i++) {
      String s = getFile().getText();

      int offset;
      while (true) {
        offset = random.nextInt(s.length());
        if (CharArrayUtil.regionMatches(s, offset, "/**/") || CharArrayUtil.regionMatches(s, offset, "//")) {
          break;
        }
      }

      char next = offset < s.length()-1 ? s.charAt(offset+1) : 0;
      if (next == '/') {
        getEditor().getCaretModel().moveToOffset(offset + 1);
        type("**");
        unused--;
      }
      else if (next == '*') {
        getEditor().getCaretModel().moveToOffset(offset + 1);
        delete();
        delete();
        unused++;
      }
      else {
        continue;
      }
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      getFile().accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          assertTrue(element.toString(), element.isValid());
          super.visitElement(element);
        }
      });

      //System.out.println("i = " + i + " " + next + " at "+offset);

      List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
      errors = DaemonAnalyzerTestCase.filter(infos, HighlightSeverity.ERROR);
      assertEmpty(errors);
      List<HighlightInfo> warns = DaemonAnalyzerTestCase.filter(infos, HighlightSeverity.WARNING);
      if (unused != warns.size()) {
        assertEquals(warns.toString(), unused, warns.size());
      }
    }
    FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
  }

  private static boolean accessible(PsiClass aClass, Set<PsiClass> visited) {
    if (!visited.add(aClass)) return false;
    // this class and all its super- and containing- classes should be public
    if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    for (PsiClass superClass : aClass.getSupers()) {
      if (!accessible(superClass, visited)) return false;
    }
    PsiClass containingClass = aClass.getContainingClass();
    return containingClass == null || accessible(containingClass, visited);
  }

  public void testRandomTypingAndDeletingCommentAndCheckingNoHighlightsAreLeftInside() {
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
    configureFromFileText("A.java", t.toString());

    List<HighlightInfo> initErrors = ContainerUtil.sorted(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR), HighlightInfoUpdaterImpl.BY_OFFSETS_AND_HASH_ERRORS_FIRST);

    int N = 100;

    // check that inserting /*--*/ inside whitespace does not crash anything
    for (int i = 0; i < N; i++) {
      try {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        LOG.debug("i = " + i);
        String s = getFile().getText();
        int offset = random.nextInt(20/*do not mess with * in imports*/,s.length()-5);
        getEditor().getCaretModel().moveToOffset(offset);
        type("/*-");
        List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
        assertNotEmpty(infos);

        PsiElement comment = SyntaxTraverser.psiTraverser(getFile()).filter(e -> e instanceof PsiComment && e.getText().startsWith("/*-")).toList().getFirst();

        List<HighlightInfo> collect = new ArrayList<>();
        TextRange cRange = comment.getTextRange().cutOut(TextRange.create(4, comment.getTextRange().getLength()-1)); // to strictly contain
        //noinspection ConstantValue
        DaemonCodeAnalyzerEx.processHighlights(getEditor().getDocument(), getProject(), HighlightSeverity.ERROR, cRange.getStartOffset(), cRange.getEndOffset(),
                                               h -> !cRange.contains(h) || !collect.add(h));
        assertEmpty("Found error inside comment: "+collect+"; seed="+seed, collect);

        backspace();
        backspace();
        backspace();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        List<HighlightInfo> after = ContainerUtil.sorted(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR), HighlightInfoUpdaterImpl.BY_OFFSETS_AND_HASH_ERRORS_FIRST);
        assertEquals("highlighters didn't restore; seed="+seed, initErrors, after);
      }
      catch (Throwable e) {
        System.err.println("Error running with seed "+seed);
        throw e;
      }
    }
  }

  private static String genRandomChar(Random random) {
    int leftLimit = 48; // numeral '0'
    int rightLimit = 122; // letter 'z'

    return Character.toString(random.nextInt(leftLimit, rightLimit + 1));
  }

  private int withTypedLetter(int num, String s, Random random, Runnable in) {
    int length = getEditor().getDocument().getTextLength();
    int errOffset = random.nextInt(20/*do not mess with * in imports*/,length-5);
    WriteCommandAction.runWriteCommandAction(getProject(), ()->getEditor().getDocument().insertString(errOffset, s)); // do not call type() because of overtyping risks
    try {
      in.run();
    }
    finally {
      WriteCommandAction.runWriteCommandAction(getProject(), ()-> {
        getEditor().getDocument().deleteString(errOffset, errOffset+s.length());
      }); // do not call type() because of overtyping risks
    }
    return errOffset;
  }
  private @NotNull List<HighlightInfo> doHighlightAndSort(long seed) {
    List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    List<HighlightInfo> sorted = ContainerUtil.sorted(infos, Comparator.comparing(h->text(h)));
    for (HighlightInfo info : sorted) {
      assertTrue(info.getHighlighter().isValid());
    }
    assertNoDuplicateHighlighters(DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true), seed);
    return sorted;
  }

  public void testCheckThat8812599550819766708LTypingDoesNotCreateDuplicateRangeHighlighters() {
    long seed =8812599550819766708L;
    Random random = new Random(seed);
    randomTypeAndCheckForDuplicates(seed, random);
  }
  public void testCheckThatRandomTypingDoesNotCreateDuplicateRangeHighlighters() {
    long seed =//-1816450852840074871L;
    new Random().nextLong();
    Random random = new Random(seed);
    randomTypeAndCheckForDuplicates(seed, random);
  }

  private void randomTypeAndCheckForDuplicates(long seed, Random random) {
    HighlightInfoUpdaterImpl updater = (HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(getProject());
    updater.runAssertingInvariants(() -> {
      disableHyperlinkAnnotator();
      configureFromFileText("A.java", "import java.util.*;\n" + classText.repeat(1/*0*/));
      List<HighlightInfo> initErrors = doHighlightAndSort(seed);
      assertNotEmpty(initErrors);

      int N = 50;

      try {
        typeAndCheck(seed, N, random, initErrors);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
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
    point.maskAll(exceptHyper, getTestRootDisposable(), false);
  }

  private void assertNoDuplicateHighlighters(MarkupModel markupModel, long seed) {
    record HI(TextRange range, String desc, TextAttributes attributes){}
    Set<HI> set = new HashSet<>();
    List<RangeHighlighter> highlighters = new ArrayList<>();
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
      if (info == null
          || info.getToolId() == HighlightVisitorImpl.class && TextRange.create(info).isEmpty()
          || info.getToolId() == HighlightVisitorImpl.class && TextRange.create(info).getLength()==1 && info.getDescription() != null && StringUtil.endsWith(info.getDescription(), " expected")
      ) { // ignore empty error elements, there are can be multiple at the same offset after reparse
        continue;
      }
      highlighters.add(highlighter);
      HI hi = new HI(highlighter.getTextRange(), info.getDescription(), info.getTextAttributes(null, getEditor().getColorsScheme()));
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
    TextRange textRange = FileStatusMap.getDirtyTextRange(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
    List<HighlightInfo> after = doHighlightAndSort(seed);
    String afterText = StringUtil.join(after, h -> text(h), "\n");
    if (!initInfoText.equals(afterText)) {
      List<HighlightInfo> a3 = doHighlightAndSort(seed);
      String actualInfoText = StringUtil.join(a3, h -> text(h), "\n");
      assertEquals("highlighters didn't restore; seed=" + seed+"; num="+num, initInfoText, actualInfoText);
    }
    return afterText;
  }

  public void testCheckThatTypingRandomErrorsAndThenOpenJavadocCommentDoesGenerateGiantCommentedTextButDoesNotLeaveErrorsInsideCommentedOutText() {
    disableHyperlinkAnnotator();
    long seed = //2518799306412815874L;
    new Random().nextLong();
    Random random = new Random(seed);
    configureFromFileText("A.java", "import java.util.*;\n" + classText.repeat(5));

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
              List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
              assertNotEmpty(infos);

              PsiElement comment = SyntaxTraverser.psiTraverser(getFile()).filter(e -> e instanceof PsiComment && e.getText().startsWith("/*")).toList().getFirst();

              List<HighlightInfo> collect = new ArrayList<>();
              TextRange cringe =
                comment.getTextRange().cutOut(TextRange.create(4, comment.getTextRange().getLength() - 1)); // to strictly contain
              //noinspection ConstantValue
              DaemonCodeAnalyzerEx.processHighlights(getEditor().getDocument(), getProject(), HighlightInfoType.SYMBOL_TYPE_SEVERITY,
                                                     cringe.getStartOffset(), cringe.getEndOffset(),
                                                     h -> !(cringe.contains(h) && collect.add(h)));
              assertEmpty("Found stray highlighting inside comment: " + collect + "; seed=" + seed, collect);
            });
          });
        });

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        List<HighlightInfo> after = doHighlightAndSort(seed);
        assertEquals("highlighters didn't restore; seed="+seed, initErrors, after);
      }
      catch (Throwable e) {
        System.err.println("Error running with seed "+seed+"L");
        throw e;
      }
    }
  }
}
