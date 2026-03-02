// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.analysis.OptimizeImportRestarter;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.naming.ClassNamingConvention;
import com.siyeh.ig.naming.NewClassNamingConventionInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class ImportHelperTest extends ProductionDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 100;
    enableInspectionTool(new UnusedImportInspection());
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
    //noinspection SuperTearDownInFinally
    super.tearDown();
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk17(); // Java 8 mock does not have java.sql package used here
  }

  private PsiJavaFile configureByText(String text) {
    configureByText(JavaFileType.INSTANCE, text);
    assertTrue(getFile() instanceof PsiJavaFile);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)getEditor());
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
    return (PsiJavaFile)getFile();
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    assertResolveNotCalledInEDTDuring(() -> isFromJavaCodeReferenceElementResolve(), () -> {
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
        if (isFromResolve) {
          resolveHappened.set(true);
        }
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
    assertEmpty(waitHighlightingSurviveCancellations());
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
    assertEmpty(waitHighlightingSurviveCancellations());
    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {

          JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
          javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
          PackageEntryTable table = new PackageEntryTable();
          table.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
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
    assertEmpty(waitHighlightingSurviveCancellations());

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

    assertOneElement(waitHighlightingSurviveCancellations());

    int offset = getEditor().getCaretModel().getOffset();
    PsiReference ref = getFile().findReferenceAt(offset - 1);
    assertTrue(ref instanceof PsiJavaCodeReferenceElement);

    ImportClassFix fix = createImportFix((PsiJavaCodeReferenceElement)ref);
    ImportClassFixBase.Result result = fix.doFix(getEditor(), true, false, true);
    assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, result);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    getEditor().getCaretModel().moveToOffset(offset - 1);
    fix = createImportFix((PsiJavaCodeReferenceElement)ref);
    result = fix.doFix(getEditor(), true, false, true);
    assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    assertEmpty(waitHighlightingSurviveCancellations());
  }
  public void testAutoImportCaretLocationNotImportIfResolved() throws ExecutionException, InterruptedException {

    String text = """
      package org.example;
      
      import static org.example.MyEnum.ABC;
      
      
      public class Main {
      
        static void main() {
      
      
            if (<caret> ABC.equals(getSomeEnum())) {
            System.out.println("test");
          }
        }
      
        private static MyEnum getSomeEnum() {
          return MyEnum.XYZ;
        }
      }
      
      
      class MyConstants {
        public static final String FOO = "foo";
      
        public static class DEFAULT {
          public static final String x = "x";
          public static final Integer y = 50;
        }
      
        public static class ABC {
          public static final String BAR = "bar";
          public static final Integer BAZ = null;
        }
      
        public static class DEF {
          public static final String BLAA = "BLAA";
        }
      }
      
      enum MyEnum {
        ABC("abc"),
      
        XYZ("xzy");
      
        private String value;
      
        MyEnum(String value) {
          this.value = value;
        }
      
        public String getValue() {
          return value;
        }
      
        @Override
        public String toString() {
          return value;
        }
      
      }
      """;
    configureByText(text);

    ThrowableComputable<BooleanSupplier, RuntimeException> computable = () ->
      new JavaReferenceImporter()
        .computeAutoImportAtOffset(getEditor(), getFile(), getEditor().getCaretModel().getOffset(), true);

    BooleanSupplier supplier = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      return ReadAction.compute(computable);
    }).get();
    assertNull(supplier);
  }

  private static ImportClassFix createImportFix(PsiJavaCodeReferenceElement ref) throws InterruptedException, ExecutionException {
    return ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> new ImportClassFix(ref))).get();
  }

  public void testAutoImportCaretLocation2() throws ExecutionException, InterruptedException {
    String text = "class X { <caret>ArrayList c = null; }";
    configureByText(text);
    type(" ");
    backspace();

    assertSize(1, waitHighlightingSurviveCancellations());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    int offset = getEditor().getCaretModel().getOffset();
    PsiReference ref = getFile().findReferenceAt(offset);
    assertTrue(ref instanceof PsiJavaCodeReferenceElement);

    ImportClassFixBase.Result result = createImportFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false, true);
    assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    assertEmpty(waitHighlightingSurviveCancellations());
  }

  public void testAutoImportWorksWhenITypeSpaceAfterClassName() throws Exception {
    @NonNls String text = "class S { ArrayList<caret> }";
    configureByText(text);

    waitHighlightingSurviveCancellations();
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

  public void testAutoImportHintIsShownEvenForNonErrorHighlightInfo() {
    @Language("JAVA")
    String text = """
      class S { XXX456 x; // for resolve
        //showme<caret>
      }
      """;
    MyHintInspection tool = new MyHintInspection();
    enableInspectionTool(tool);
    SHOWN.set(false);
    configureByText(text);
    type(" xxx"); // make undoable to enable showing autoimports
    waitHighlightingSurviveCancellations();
    assertTrue(SHOWN.get());
  }
  public void testAutoImportHintIsNotShownAfterEscapePressed() {
    @Language("JAVA")
    String text = """
      class S { XXX456 x; // for resolve
        //showme<caret>
      }
      """;
    MyHintInspection tool = new MyHintInspection();
    enableInspectionTool(tool);
    SHOWN.set(false);
    configureByText(text);
    type(" xxx"); // make undoable to enable showing autoimports
    waitHighlightingSurviveCancellations();
    assertTrue(SHOWN.get());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    getEditor().getSelectionModel().setSelection(0,null, 1); // to enable escape
    escape();
    SHOWN.set(false);
    waitHighlightingSurviveCancellations();
    assertFalse(SHOWN.get());
  }

  private static final AtomicBoolean SHOWN = new AtomicBoolean();
  private static class MyHintInspection extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getShortName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return "MyHintInspTst";
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                   boolean isOnTheFly,
                                                   @NotNull LocalInspectionToolSession session) {
      return new JavaElementVisitor() {
        @Override
        public void visitComment(@NotNull PsiComment comment) {
          if (comment.getText().contains("showme")) {
            holder.registerProblem(comment, "showerr", new MyHint());
          }
        }
      };
    }
    private static class MyHint implements LocalQuickFix, HintAction {
      @Override
      public boolean showHint(@NotNull Editor editor) {
        SHOWN.set(true);
        return true;
      }

      @Override
      public @NotNull String getText() {
        return "showme";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        SHOWN.set(true);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }

      @Override
      public @NotNull String getFamilyName() {
        return getText();
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        SHOWN.set(true);
      }
    }
  }


  public void testAutoImportAfterUncomment() throws ExecutionException, InterruptedException {
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("java.util.ArrayList", GlobalSearchScope.allScope(getProject())));
    @Language("JAVA")
    @NonNls String text = "class S { /*ArrayList l; HashMap h; <caret>*/ }";
    configureByText(text);

    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    waitHighlightingSurviveCancellations();

    assertNoImportsAdded();

    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_COMMENT_BLOCK);

    assertEmpty(waitHighlightingSurviveCancellations());
    waitForAutoOptimizeImports();

    assertSize(2, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
  }

  @NotNull
  private Editor createSaveAndOpenFile(@NotNull String relativePath, @NotNull String fileText) throws IOException {
    File tempFile = new File(createTempDirectory(), relativePath);
    tempFile.getParentFile().mkdirs();
    tempFile.createNewFile();
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
    assert vFile != null;
    WriteAction.runAndWait(() -> {
      vFile.setCharset(StandardCharsets.UTF_8);
      VfsUtil.saveText(vFile, fileText);
    });

    PsiTestUtil.addSourceRoot(myModule, vFile.getParent());

    configureByExistingFile(vFile);
    return getEditor();
  }

  public void testUnresolvedReferenceQuickFixMustReappearAfterTheClassUnderQuestionIsCreated() throws Exception {
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    @Language("JAVA")
    String otherText = """
      package x;
      public class OtherClass {
       //
      }""";

    Editor otherEditor = createSaveAndOpenFile("x/OtherClass.java", otherText);
    @Language("JAVA")
    @NonNls String text = """
      <caret>
      class S {
        SomeOtherMethodClass12 t;
       }""";
    configureByText(text);
    JavaCodeStyleSettings javaCodeStyleSettings = CodeStyle.getSettings(getFile()).getCustomSettings(JavaCodeStyleSettings.class);
    javaCodeStyleSettings.INSERT_INNER_CLASS_IMPORTS = true;

    HighlightInfo error = assertOneElement(waitHighlightingSurviveCancellations());
    assertEquals("Cannot resolve symbol 'SomeOtherMethodClass12'", error.getDescription());

    assertNoImportsAdded();

    otherEditor.getCaretModel().moveToOffset(otherText.indexOf("//")); //before //
    @Language("JAVA")
    String toType = "public static class SomeOtherMethodClass12 {}";
    for (int i = 0; i < toType.length(); i++) {
      char c = toType.charAt(i);
      EditorTestUtil.performTypingAction(otherEditor, c);
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNotNull(PsiDocumentManager.getInstance(getProject()).getPsiFile(otherEditor.getDocument()));

    assertEmpty(waitHighlightingSurviveCancellations());
    waitForAutoOptimizeImports();
    assertOneImportAdded("x.OtherClass.SomeOtherMethodClass12");
    assertSize(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
  }

  public void testEnsureOptimizeImportsWhenInspectionReportsErrors() throws Exception {
    @NonNls String text = "import java.util.List; class S { } <caret>";
    configureByText(text);
    //ensure error will be provided by a local inspection
    NewClassNamingConventionInspection tool = new MyNewClassNamingConventionInspection();
    tool.setEnabled(true, ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
    enableInspectionTool(tool);

    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());

    List<HighlightInfo> errs = waitHighlightingSurviveCancellations();
    //error corresponding to too short class name
    assertSize(1, errs);

    assertOneImportAdded("java.util.List");

    type("/* */");
    waitHighlightingSurviveCancellations();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    assertNoImportsAdded();
  }

  public void testAutoImportWorks() {
    @NonNls final String text = "class S { JFrame x; <caret> }";
    configureByText(text);
    boolean isInContent = true;
    assertFalse(DaemonListeners.canChangeFileSilently(getFile(), isInContent, ThreeState.UNSURE));


    waitHighlightingSurviveCancellations();
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

    waitHighlightingSurviveCancellations();
    //caret is too close
    assertNoImportsAdded();

    caretRight();

    waitHighlightingSurviveCancellations();

    assertOneImportAdded("java.util.ArrayList");
  }

  private void assertOneImportAdded(String s) throws Exception {
    waitForAutoOptimizeImports();
    PsiImportStatementBase importStatement = assertOneElement(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
    PsiElement resolved = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> importStatement.resolve())).get();
    assertTrue(resolved instanceof PsiClass);
    assertEquals(s, ((PsiClass)resolved).getQualifiedName());
  }
  private void assertNoImportsAdded() throws ExecutionException, InterruptedException {
    waitForAutoOptimizeImports();
    assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
  }

  public void testAutoOptimizeUnresolvedImports() throws ExecutionException, InterruptedException {
    @NonNls String text = "import xxx.yyy; class S { } <caret> ";
    configureByText(text);

    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> errs = waitHighlightingSurviveCancellations();
    waitForAutoOptimizeImports();

    //error in import list
    assertSize(1, errs);

    assertSize(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements());

    type("/* */");
    waitHighlightingSurviveCancellations();

    assertNoImportsAdded();
  }

  private void waitForAutoOptimizeImports() throws InterruptedException, ExecutionException {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    TestDaemonCodeAnalyzerImpl.waitWhilePumping(ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        OptimizeImportRestarter.getInstance(getProject()).waitForScheduledOptimizeImportRequestsInTests();
      }
      catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }));
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
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
      waitHighlightingSurviveCancellations();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

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

    List<HighlightInfo> errs = waitHighlightingSurviveCancellations();

    assertEmpty(errs);

    type("/* ");
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    errs = waitHighlightingSurviveCancellations();
    assertNotEmpty(errs);
    assertOneImportAdded("java.util.ArrayList");
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    type(" */ ");
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    errs = waitHighlightingSurviveCancellations();
    assertEmpty(errs);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    assertOneImportAdded("java.util.ArrayList");
  }

  public void testAutoInsertImportForInnerClass() throws ExecutionException, InterruptedException {
    @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
    configureByText(text);

    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;

    List<HighlightInfo> errs = waitHighlightingSurviveCancellations();
    assertSize(1, errs);

    assertNoImportsAdded();
    type("/* */");
    waitHighlightingSurviveCancellations();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
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
    waitHighlightingSurviveCancellations();

    assertOneImportAdded("java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock");
  }

  public void testAutoImportSkipsClassReferenceInMethodPosition() throws Exception {
    @NonNls String text =
      "package x; import java.util.HashMap; class S { HashMap<String,String> f(){ return  Hash<caret>Map <String, String >();} }  ";
    configureByText(text);

    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;

    List<HighlightInfo> errs = waitHighlightingSurviveCancellations();
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

    assertSize(1, waitHighlightingSurviveCancellations());
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
    waitHighlightingSurviveCancellations();
    assertOneImportAdded("java.util.ArrayList");
  }

  private @NotNull List<HighlightInfo> waitHighlightingSurviveCancellations() {
    while (true) {
      try {
        return myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      }
      catch (ProcessCanceledException e) {
        // document modifications are expected here, e.g. when auto-import adds an import and cancels the current highlighting
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      }
    }
  }

  public static boolean isFromJavaCodeReferenceElementResolve() {
    Throwable currentStack = new Throwable();
    return ContainerUtil.exists(currentStack.getStackTrace(),
       stackElement -> stackElement.getClassName().equals(PsiImplUtil.class.getName())
                       && stackElement.getMethodName().equals("multiResolveImpl"));
  }

  public void testImportHintsMustBeComputedForAllUnresolvedReferencesInVisibleAreaToBeAbleToShowPopups() throws Exception {
    ThreadingAssertions.assertEventDispatchThread();
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
    @Language("JAVA")
    @NonNls final String text = "class S {{ \n" +
                                "new ArrayList();\n".repeat(1000) +
                                " }}";
    configureByText(text);

    getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(500, 10));
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100); // make sure editor is visible - auto-import works only for visible area
    getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100); // make sure editor is visible - auto-import works only for visible area
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    Editor editor = getEditor();
    TextRange visibleRange = editor.calculateVisibleRange();
    assertTrue(visibleRange.toString(), visibleRange.getStartOffset() > 5000 && visibleRange.getEndOffset() < 10_000); // sanity check that visible range has been indeed changed

    List<HighlightInfo> errors = ContainerUtil.sorted(waitHighlightingSurviveCancellations(), Segment.BY_START_OFFSET_THEN_END_OFFSET);
    assertSize(1000, errors);
    LazyQuickFixUpdaterImpl updater = (LazyQuickFixUpdaterImpl)LazyQuickFixUpdater.getInstance(getProject());
    long deadline = System.currentTimeMillis() + 60_000;
    for (int i = 0; i < errors.size(); i++) {
      HighlightInfo error = errors.get(i);
      if (visibleRange.contains(error)) { // we care only for visible errors; invisible ones may or may not be computed
        updater.waitForBackgroundJobIfStartedInTests(getProject(), editor.getDocument(), error, deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        if (!error.hasHint()) {
          List<HintAction> hints = ShowAutoImportPass.extractHints(error);
          String message = error + ": " + i + " hasHints: "+error.hasHint() + "; hints:" + hints + "; visibleRange:" + visibleRange + "; contains: " + visibleRange.contains(error);
          fail(message);
        }
      }
    }
  }

  public void testImportHintMustStillBeAvailableAfterTypingBeforeTheReference() throws Exception {
    ThreadingAssertions.assertEventDispatchThread();
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
    @Language("JAVA")
    @NonNls final String text = """
      class S {{
      new ArrayList();
       }}""";
    configureByText(text);

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    Editor editor = getEditor();

    assertHasImportHintAllOverUnresolvedReference("");
    int offset = editor.getDocument().getText().indexOf("ArrayList");
    for (int i=0; i<10; i++) {
      getEditor().getCaretModel().moveToOffset(offset+i);
      type(" ");
      assertHasImportHintAllOverUnresolvedReference(String.valueOf(i));
    }
    for (int i=0; i<10; i++) {
      getEditor().getCaretModel().moveToOffset(offset+10+i);
      type("\n");
      assertHasImportHintAllOverUnresolvedReference(String.valueOf(i));
    }
  }

  private void assertHasImportHintAllOverUnresolvedReference(String message) throws Exception {
    List<HighlightInfo> errors = ContainerUtil.sorted(waitHighlightingSurviveCancellations(), Segment.BY_START_OFFSET_THEN_END_OFFSET);
    assertNotEmpty(errors);
    HighlightInfo error = errors.getFirst();
    assertEquals(message, "Cannot resolve symbol 'ArrayList'", error.getDescription());
    assertTrue(message, error.hasHint());
    HighlightInfo.IntentionActionDescriptor errDesc = error.findRegisteredQuickFix((descriptor, range) -> descriptor.getAction().getText().startsWith("Import class") ? descriptor : null);
    assertNotNull(errDesc);
    assertTrue(message, errDesc.getAction().isAvailable(getProject(), getEditor(), getFile()));
    for (int i = error.getActualStartOffset(); i < error.getActualEndOffset(); i++) {
      getEditor().getCaretModel().moveToOffset(i);
      List<HighlightInfo.IntentionActionDescriptor> errDescriptors = ReadAction.nonBlocking(()->ShowIntentionsPass.getActionsToShow(getEditor(), getFile()).errorFixesToShow).submit(AppExecutorUtil.getAppExecutorService()).get();
      HighlightInfo.IntentionActionDescriptor importDesc = ContainerUtil.find(errDescriptors, descriptor -> descriptor.getAction().getText().startsWith("Import class"));
      assertNotNull(message + ": " + i, importDesc);
      assertTrue(message, importDesc.getAction().isAvailable(getProject(), getEditor(), getFile()));
    }
  }
}
