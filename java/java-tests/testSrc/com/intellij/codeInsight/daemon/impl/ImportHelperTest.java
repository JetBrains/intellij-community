// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.naming.ClassNamingConvention;
import com.siyeh.ig.naming.NewClassNamingConventionInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class ImportHelperTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 100;
    DaemonCodeAnalyzer.getInstance(getProject()).setUpdateByTimerEnabled(false);
    enableInspectionTool(new UnusedImportInspection());
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
    //noinspection SuperTearDownInFinally
    super.tearDown();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_1_7; // Java 8 mock does not have java.sql package used here
  }

  private PsiJavaFile configureByText(String text) {
    configureFromFileText("dummy.java", text);
    assertTrue(getFile() instanceof PsiJavaFile);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)getEditor());
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
    return (PsiJavaFile)getFile();
  }

  @Override
  protected boolean isRunInCommand() {
    // Avoid starting inside command (as implemented in super-class)
    // because we need to operate on application undo queue
    return false;
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) {
    assertResolveNotCalledInEDTDuring(ImportHelperTest::isFromJavaCodeReferenceElementResolve, () -> {
      try {
        super.runTestRunnable(testRunnable);
      }
      catch (Throwable e) {
        ExceptionUtil.rethrow(e);
      }
    });
  }

  public static void assertResolveNotCalledInEDTDuring(@NotNull BooleanSupplier isInsideResolve, @NotNull Runnable runnable) {
    ThreadingAssertions.assertEventDispatchThread();
    AtomicBoolean resolveHappened = new AtomicBoolean();
    // we run the test in EDT under this fake progress which is needed for one thing only: to be able to assert that no resolve happens in EDT.
    // Since resolve calls checkCanceled() a lot, we intercept these calls and check is we are being called from outside of EDT
    class MyPingProgressIndicator extends ProgressIndicatorBase implements PingProgress {
      @Override
      public void interact() {
        if (resolveHappened.get()
            && (!ApplicationManager.getApplication().isDispatchThread() && ApplicationManager.getApplication().isReadAccessAllowed()
                || ApplicationManager.getApplication().isWriteAccessAllowed())) {
          return; // optimization: try not to call getStackTrace() if we can
        }
        boolean isFromResolve = isInsideResolve.getAsBoolean();
        if (isFromResolve) resolveHappened.set(true);
        assertTrue("Resolve in EDT happened",
          !isFromResolve
               || !ApplicationManager.getApplication().isDispatchThread() && ApplicationManager.getApplication().isReadAccessAllowed() // allow resolve from the background thread
               || ApplicationManager.getApplication().isWriteAccessAllowed()); // allow resolve in the write action because that's how PsiReference.bindToElement() works
      }
    }
    ProgressManager.getInstance().executeProcessUnderProgress(runnable, new MyPingProgressIndicator());
    assertTrue("It seems there wasn't any resolve in this test at all. (or maybe `isFromJavaCodeReferenceElementResolve()` method is broken?)", resolveHappened.get());
  }

  public void testImportsInsertedAlphabetically() {
    @Language("JAVA")
    @NonNls String text = "class I {}";
    final PsiJavaFile file = configureByText(text);
    assertEmpty(highlightErrors());
    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
        try {
          checkAddImport(file, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_LIST);
          checkAddImport(file, "java.util.ArrayList", "java.util.ArrayList", CommonClassNames.JAVA_UTIL_LIST);
          checkAddImport(file, "java.util.HashMap", "java.util.ArrayList", "java.util.HashMap", CommonClassNames.JAVA_UTIL_LIST);
          checkAddImport(file, "java.util.SortedMap", "java.util.ArrayList", "java.util.HashMap", CommonClassNames.JAVA_UTIL_LIST,
                         "java.util.SortedMap");
          checkAddImport(file, CommonClassNames.JAVA_UTIL_MAP, "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap");
          checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap");
          checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap");
          checkAddImport(file, "java.util.TreeMap", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.TreeMap");
          checkAddImport(file, "java.util.concurrent.atomic.AtomicBoolean", "java.util.AbstractList", "java.util.ArrayList",
                         "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.TreeMap",
                         "java.util.concurrent.atomic.AtomicBoolean");
          checkAddImport(file, "java.io.File", "java.io.File", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.TreeMap",
                         "java.util.concurrent.atomic.AtomicBoolean");
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }), "", "");
  }

  public void testStaticImportsGrouping() {
    @Language("JAVA")
    @NonNls String text = """
      import static java.lang.Math.max;
      import java.util.Map;

      import static java.lang.Math.min;

      import java.awt.Component;



      import static javax.swing.SwingConstants.CENTER;
      /** @noinspection ALL*/ class I {{ max(0, 0); Map.class.hashCode(); min(0,0); Component.class.hashCode(); int i = CENTER; }}""";

    final PsiJavaFile file = configureByText(text);
    assertEmpty(highlightErrors());
    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {

          JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
          javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
          PackageEntryTable table = new PackageEntryTable();
          table.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
          table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          table.addEntry(new PackageEntry(false, "javax", true));
          table.addEntry(new PackageEntry(false, "java", true));
          table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          table.addEntry(new PackageEntry(true, "java", true));
          table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          table.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);

          JavaCodeStyleSettings.getInstance(getProject()).IMPORT_LAYOUT_TABLE.copyFrom(table);
          JavaCodeStyleManager.getInstance(getProject()).optimizeImports(file);

          assertOrder(file, "java.awt.*", CommonClassNames.JAVA_UTIL_MAP, "static java.lang.Math.max", "static java.lang.Math.min",
                      "static javax.swing.SwingConstants.CENTER");
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }), "", "");
  }

  private void checkAddImport(PsiJavaFile file, String fqn, String... expectedOrder) {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
    ImportHelper importHelper = new ImportHelper(settings);

    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(fqn, GlobalSearchScope.allScope(getProject()));
    boolean b = importHelper.addImport(file, psiClass);
    assertTrue(b);

    assertOrder(file, expectedOrder);
  }

  private static void assertOrder(@NotNull PsiJavaFile file, @NonNls String @NotNull ... expectedOrder) {
    PsiImportStatementBase[] statements = file.getImportList().getAllImportStatements();

    assertSize(expectedOrder.length, statements);
    for (int i = 0; i < statements.length; i++) {
      PsiImportStatementBase statement = statements[i];
      String text = StringUtil.trimEnd(StringUtil.trimStart(statement.getText(), "import "), ";");
      assertEquals(expectedOrder[i], text);
    }
  }

  public void testConflictingClassesFromCurrentPackage() {
    @Language("JAVA")
    String text = "package java.util; class X{ Date d;}";
    final PsiJavaFile file = configureByText(text);
    assertEmpty(highlightErrors());

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
      ImportHelper importHelper = new ImportHelper(settings);

      PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("java.sql.Date", GlobalSearchScope.allScope(getProject()));
      boolean b = importHelper.addImport(file, psiClass);
      assertFalse(b); // must fail;
    });
  }

  public void testAutoImportCaretLocation() throws ExecutionException, InterruptedException {
    String text = "class X { ArrayList<caret> c; }";
    configureByText(text);
    type(" ");
    backspace();

    assertOneElement(highlightErrors());

    int offset = getEditor().getCaretModel().getOffset();
    PsiReference ref = getFile().findReferenceAt(offset - 1);
    assertTrue(ref instanceof PsiJavaCodeReferenceElement);

    ImportClassFix fix = createImportFix((PsiJavaCodeReferenceElement)ref);
    ImportClassFixBase.Result result = fix.doFix(getEditor(), true, false, true);
    assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, result);
    UIUtil.dispatchAllInvocationEvents();

    getEditor().getCaretModel().moveToOffset(offset - 1);
    fix = createImportFix((PsiJavaCodeReferenceElement)ref);
    result = fix.doFix(getEditor(), true, false, true);
    assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
    UIUtil.dispatchAllInvocationEvents();

    assertEmpty(highlightErrors());
  }

  private static ImportClassFix createImportFix(PsiJavaCodeReferenceElement ref) throws InterruptedException, ExecutionException {
    return ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> new ImportClassFix(ref))).get();
  }

  public void testAutoImportCaretLocation2() throws ExecutionException, InterruptedException {
    String text = "class X { <caret>ArrayList c = null; }";
    configureByText(text);
    type(" ");
    backspace();

    assertSize(1, highlightErrors());
    UIUtil.dispatchAllInvocationEvents();

    int offset = getEditor().getCaretModel().getOffset();
    PsiReference ref = getFile().findReferenceAt(offset);
    assertTrue(ref instanceof PsiJavaCodeReferenceElement);

    ImportClassFixBase.Result result = createImportFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false, true);
    assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
    UIUtil.dispatchAllInvocationEvents();

    assertEmpty(highlightErrors());
  }

  public void testAutoImportWorksWhenITypeSpaceAfterClassName() throws Exception {
    @NonNls String text = "class S { ArrayList<caret> }";
    configureByText(text);

    doHighlighting();
    //caret is too close
    assertNoImportsAdded();

    type(" ");

    PsiJavaCodeReferenceElement element =
      (PsiJavaCodeReferenceElement)getFile().findReferenceAt(getEditor().getCaretModel().getOffset() - 2);
    ImportClassFix fix = createImportFix(element);
    ImportClassFixBase.Result result = fix.doFix(getEditor(), false, false, true);
    assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);

    assertOneImportAdded("java.util.ArrayList");
  }

  public void testAutoImportAfterUncomment() {
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("java.util.ArrayList", GlobalSearchScope.allScope(getProject())));
    @Language("JAVA")
    @NonNls String text = "class S { /*ArrayList l; HashMap h; <caret>*/ }";
    configureByText(text);

    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    doHighlighting();

    assertNoImportsAdded();

    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_COMMENT_BLOCK);

    doHighlighting();
    UIUtil.dispatchAllInvocationEvents();

    assertEmpty(highlightErrors());

    assertSize(2, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
  }

  public void testEnsureOptimizeImportsWhenInspectionReportsErrors() throws Exception {
    @NonNls String text = "import java.util.List; class S { } <caret>";
    configureByText(text);
    //ensure error will be provided by a local inspection
    NewClassNamingConventionInspection tool = new MyNewClassNamingConventionInspection();
    tool.setEnabled(true, ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
    enableInspectionTool(tool);

    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());

    List<HighlightInfo> errs = highlightErrors();
    //error corresponding to too short class name
    assertSize(1, errs);

    assertOneImportAdded("java.util.List");

    type("/* */");
    doHighlighting();
    UIUtil.dispatchAllInvocationEvents();
    assertNoImportsAdded();
  }

  public void testAutoImportWorks() {
    @NonNls final String text = "class S { JFrame x; <caret> }";
    configureByText(text);
    boolean isInContent = true;
    assertFalse(DaemonListeners.canChangeFileSilently(getFile(), isInContent, ThreeState.UNSURE));


    doHighlighting();
    assertFalse(DaemonListeners.canChangeFileSilently(getFile(), isInContent, ThreeState.UNSURE));

    type(" ");
    assertTrue(DaemonListeners.canChangeFileSilently(getFile(), isInContent, ThreeState.UNSURE));

    UndoManager.getInstance(getProject()).undo(TextEditorProvider.getInstance().getTextEditor(getEditor()));

    assertFalse(DaemonListeners.canChangeFileSilently(getFile(), isInContent, ThreeState.UNSURE));
  }


  public void testAutoImportOfGenericReference() throws Exception {
    @NonNls final String text = "class S {{ new ArrayList<caret><String> }}";
    configureByText(text);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000); // make sure editor is visible - auto-import works only for visible area
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    type(" ");
    backspace();

    doHighlighting();
    //caret is too close
    assertNoImportsAdded();

    caretRight();

    doHighlighting();

    assertOneImportAdded("java.util.ArrayList");
  }

  private void assertOneImportAdded(String s) throws Exception {
    PsiImportStatementBase importStatement = assertOneElement(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
    PsiElement resolved = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> importStatement.resolve())).get();
    assertTrue(resolved instanceof PsiClass);
    assertEquals(s, ((PsiClass)resolved).getQualifiedName());
  }
  private void assertNoImportsAdded() {
    assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
  }

  public void testAutoOptimizeUnresolvedImports() {
    @NonNls String text = "import xxx.yyy; class S { } <caret> ";
    configureByText(text);

    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> errs = highlightErrors();

    //error in import list
    assertSize(1, errs);

    assertSize(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements());

    type("/* */");
    doHighlighting();
    UIUtil.dispatchAllInvocationEvents();

    assertNoImportsAdded();
  }

  public void testUnambiguousImportMustBeInsertedEvenWhenShowImportPopupIsOff() throws Exception {
    @Language("JAVA")
    @NonNls String text = """
                          package p;
                          class S { ArrayList l; }
                          """;
    boolean importHintEnabled = DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled();
    try {
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;

      configureByText(text);
      type(" ");
      backspace();
      highlightErrors();
      UIUtil.dispatchAllInvocationEvents();

      assertOneImportAdded("java.util.ArrayList");
    }
    finally {
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(importHintEnabled);
    }
  }

  public void testAutoOptimizeDoesntSuddenlyRemoveImportsDuringTyping() throws Exception {
    @NonNls String text = "package x; " +
                          "import java.util.ArrayList; " +
                          "class S {{ <caret> ArrayList l;\n" +
                          "}}";
    configureByText(text);

    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());

    List<HighlightInfo> errs = highlightErrors();

    assertEmpty(errs);

    type("/* ");
    UIUtil.dispatchAllInvocationEvents();
    errs = highlightErrors();
    assertNotEmpty(errs);
    assertOneImportAdded("java.util.ArrayList");
    UIUtil.dispatchAllInvocationEvents();

    type(" */ ");
    UIUtil.dispatchAllInvocationEvents();
    errs = highlightErrors();
    assertEmpty(errs);
    UIUtil.dispatchAllInvocationEvents();

    assertOneImportAdded("java.util.ArrayList");
  }

  public void testAutoInsertImportForInnerClass() {
    @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
    configureByText(text);

    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;

    List<HighlightInfo> errs = highlightErrors();
    assertSize(1, errs);

    assertNoImportsAdded();
    type("/* */");
    doHighlighting();
    UIUtil.dispatchAllInvocationEvents();
    assertNoImportsAdded();
  }

  public void testAutoInsertImportForInnerClassAllowInnerClassImports() throws Exception {
    @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
    configureByText(text);

    JavaCodeStyleSettings javaCodeStyleSettings = CodeStyle.getSettings(getFile()).getCustomSettings(JavaCodeStyleSettings.class);
    javaCodeStyleSettings.INSERT_INNER_CLASS_IMPORTS = true;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    type(" ");
    highlightErrors();

    assertOneImportAdded("java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock");
  }

  public void testAutoImportSkipsClassReferenceInMethodPosition() throws Exception {
    @NonNls String text =
      "package x; import java.util.HashMap; class S { HashMap<String,String> f(){ return  Hash<caret>Map <String, String >();} }  ";
    configureByText(text);

    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;

    List<HighlightInfo> errs = highlightErrors();
    assertTrue(errs.size() > 1);

    PsiJavaFile javaFile = (PsiJavaFile)getFile();
    assertOneImportAdded("java.util.HashMap");

    PsiReference ref = javaFile.findReferenceAt(getEditor().getCaretModel().getOffset());
    ImportClassFix fix = createImportFix((PsiJavaCodeReferenceElement)ref);
    assertFalse(fix.isAvailable(getProject(), getEditor(), getFile()));
  }

  public void testAutoImportDoNotBreakCode() {
    @NonNls String text = "package x; class S {{ S.<caret>\n Runnable r; }}";
    configureByText(text);

    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());

    assertSize(1, highlightErrors());
  }

  public void testAutoImportIgnoresUnresolvedImportReferences() throws ExecutionException, InterruptedException {
    @Language("JAVA")
    @NonNls String text = "package x; import xxx.yyy.ArrayList; /** @noinspection ClassInitializerMayBeStatic*/ class S {{ ArrayList<caret> r; }}";
    configureByText(text);

    PsiJavaFile javaFile = (PsiJavaFile)getFile();
    PsiReference ref = javaFile.findReferenceAt(getEditor().getCaretModel().getOffset() - 1);
    ImportClassFix fix = createImportFix((PsiJavaCodeReferenceElement)ref);
    //explicitly available
    assertFalse(fix.isAvailable(getProject(), getEditor(), getFile()));
    //hint is not available
    assertFalse(fix.showHint(getEditor()));
  }

  private static class MyNewClassNamingConventionInspection extends NewClassNamingConventionInspection {
    @NotNull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
      return HighlightDisplayLevel.ERROR;
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return "Too short name";
    }

    @NotNull
    @Override
    public String getShortName() {
      return "TooShortName";
    }
  }

  public void testAutoImportMustNotRunResolveInEDT() throws Exception {
    @NonNls final String text = "class S {{ new ArrayList<<caret>String> }}";
    configureByText(text);
    type(" ");
    backspace(); // make file undoable

    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000); // make sure editor is visible - auto-import works only for visible area
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    ThreadingAssertions.assertEventDispatchThread();
    doHighlighting();
    assertOneImportAdded("java.util.ArrayList");
  }

  public static boolean isFromJavaCodeReferenceElementResolve() {
    Throwable currentStack = new Throwable();
    return ContainerUtil.exists(currentStack.getStackTrace(),
       stackElement -> stackElement.getClassName().equals(PsiImplUtil.class.getName())
                       && stackElement.getMethodName().equals("multiResolveImpl"));
  }

  public void testImportHintsMustBeComputedForAllUnresolvedReferencesInVisibleAreaToBeAbleToShowPopups() throws Exception {
    ThreadingAssertions.assertEventDispatchThread();
    @Language("JAVA")
    @NonNls final String text = "class S {{ \n" +
                                "new ArrayList();\n".repeat(1000) +
                                " }}";
    configureByText(text);

    getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(500, 10));
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100); // make sure editor is visible - auto-import works only for visible area
    getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100); // make sure editor is visible - auto-import works only for visible area
    UIUtil.dispatchAllInvocationEvents();
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    @NotNull Editor editor = getEditor();
    TextRange visibleRange = editor.calculateVisibleRange();
    assertTrue(visibleRange.toString(), visibleRange.getStartOffset() > 5000 && visibleRange.getEndOffset() < 10_000); // sanity check that visible range has been indeed changed

    List<HighlightInfo> errors = highlightErrors();
    assertNotEmpty(errors);
    UnresolvedReferenceQuickFixUpdaterImpl updater = (UnresolvedReferenceQuickFixUpdaterImpl)UnresolvedReferenceQuickFixUpdater.getInstance(getProject());
    for (HighlightInfo error : errors) {
      updater.waitForBackgroundJobIfStartedInTests(error);
      List<HintAction> hints = ShowAutoImportPass.extractHints(error);
      String message = error + " hasHints: " + error.hasHint() + "; hints:" + hints + "; visibleRange:" + visibleRange + "; contains: " + visibleRange.contains(error);
      assertEquals(message, error.hasHint(), visibleRange.contains(error));
    }
  }
}
