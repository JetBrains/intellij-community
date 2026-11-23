// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.daemon.impl.JavaReferenceImporter;
import com.intellij.codeInsight.daemon.impl.LazyQuickFixUpdaterImpl;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
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
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
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
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.naming.ClassNamingConvention;
import com.siyeh.ig.naming.NewClassNamingConventionInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture.productionDaemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
@CanChangeDocumentDuringHighlighting
public class ImportHelperTest {
  private static final Logger LOG = Logger.getInstance(ImportHelperTest.class);

  // Java 8 mock does not have java.sql package used here
  private final TestFixture<ProductionDaemonAnalyzerFixture> daemonFixture =
    productionDaemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath(), () -> LocalInspectionTool.EMPTY_ARRAY, () -> IdeaTestUtil.getMockJdk17(), null);

  @BeforeEach
  void setUp() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    JavaCodeStyleSettings.getInstance(fixture.getProject()).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 100;
    fixture.enableInspectionTool(new UnusedImportInspection());
    fixture.enableInspectionTool(new UnusedDeclarationInspection());
  }

  @AfterEach
  void tearDown() {
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
  }

  @RequiresEdt
  private PsiJavaFile configureByText(String text) {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText(JavaFileType.INSTANCE, text);
    assertTrue(fixture.getFile() instanceof PsiJavaFile);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor());
    UIUtil.markAsFocused(fixture.getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
    return (PsiJavaFile)fixture.getFile();
  }

  @RequiresEdt
  private List<HighlightInfo> waitHighlightingSurviveCancellations() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    return fixture.getTestDaemonCodeAnalyzer().waitHighlightingSurviveCancellations(fixture.getFile(), HighlightSeverity.ERROR);
  }

  private void runTest(@NotNull ThrowableRunnable<Throwable> test) {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> assertResolveNotCalledInEDTDuring(() -> isFromJavaCodeReferenceElementResolve(), () -> {
      try {
        fixture.runTest(test);
      }
      catch (Throwable e) {
        ExceptionUtil.rethrow(e);
      }
    }));
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
        assertTrue(!isFromResolve
                   || !ApplicationManager.getApplication().isDispatchThread() && ApplicationManager.getApplication().isReadAccessAllowed() // allow resolve from the background thread
                   || ApplicationManager.getApplication().isWriteAccessAllowed(), // allow resolve in the write action because that's how PsiReference.bindToElement() works
                   "Resolve in EDT happened");
      }
    }
    ProgressManager.getInstance().executeProcessUnderProgress(runnable, new MyPingProgressIndicator());
    assertTrue(resolveHappened.get(), "It seems there wasn't any resolve in this test at all. (or maybe `isFromJavaCodeReferenceElementResolve()` method is broken?)");
  }

  @Test
  public void testImportsInsertedAlphabetically() {
    runTest(() -> {
      @Language("JAVA")
      @NonNls String text = "class I {}";
      final PsiJavaFile file = configureByText(text);
      assertThat(waitHighlightingSurviveCancellations()).isEmpty();
      CommandProcessor.getInstance().executeCommand(
        daemonFixture.get().getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
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
    });
  }

  @Test
  public void testStaticImportsGrouping() {
    runTest(() -> {
      @Language("JAVA")
      @NonNls String text = """
        import static java.lang.Math.max;
        import java.util.Map;

        import static java.lang.Math.min;

        import java.awt.Component;



        import static javax.swing.SwingConstants.CENTER;
        /** @noinspection ALL*/ class I {{ max(0, 0); Map.class.hashCode(); min(0,0); Component.class.hashCode(); int i = CENTER; }}""";

      final PsiJavaFile file = configureByText(text);
      assertThat(waitHighlightingSurviveCancellations()).isEmpty();
      Project project = daemonFixture.get().getProject();
      CommandProcessor.getInstance().executeCommand(
        project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
          try {

            JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(project);
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

            JavaCodeStyleSettings.getInstance(project).IMPORT_LAYOUT_TABLE.copyFrom(table);
            JavaCodeStyleManager.getInstance(project).optimizeImports(file);

            assertOrder(file, "java.awt.*", CommonClassNames.JAVA_UTIL_MAP, "static java.lang.Math.max", "static java.lang.Math.min",
                        "static javax.swing.SwingConstants.CENTER");
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }), "", "");
    });
  }

  private void checkAddImport(PsiJavaFile file, String fqn, String... expectedOrder) {
    Project project = daemonFixture.get().getProject();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
    ImportHelper importHelper = new ImportHelper(settings);

    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    boolean b = importHelper.addImport(file, psiClass);
    assertTrue(b);

    assertOrder(file, expectedOrder);
  }

  private static void assertOrder(@NotNull PsiJavaFile file, @NonNls String @NotNull ... expectedOrder) {
    PsiImportStatementBase[] statements = file.getImportList().getAllImportStatements();

    assertThat(statements).hasSize(expectedOrder.length);
    for (int i = 0; i < statements.length; i++) {
      PsiImportStatementBase statement = statements[i];
      String text = StringUtil.trimEnd(StringUtil.trimStart(statement.getText(), "import "), ";");
      assertEquals(expectedOrder[i], text);
    }
  }

  @Test
  public void testConflictingClassesFromCurrentPackage() {
    runTest(() -> {
      @Language("JAVA")
      String text = "package java.util; class X{ Date d;}";
      final PsiJavaFile file = configureByText(text);
      assertThat(waitHighlightingSurviveCancellations()).isEmpty();

      Project project = daemonFixture.get().getProject();
      WriteCommandAction.writeCommandAction(project).run(() -> {
        JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
        ImportHelper importHelper = new ImportHelper(settings);

        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass("java.sql.Date", GlobalSearchScope.allScope(project));
        boolean b = importHelper.addImport(file, psiClass);
        assertFalse(b); // must fail;
      });
    });
  }

  @Test
  public void testAutoImportCaretLocation() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      String text = "class X { ArrayList<caret> c; }";
      configureByText(text);
      fixture.type(" ");
      fixture.backspace();

      assertThat(waitHighlightingSurviveCancellations()).hasSize(1);
      int offset = fixture.getEditor().getCaretModel().getOffset();
      PsiReference ref = fixture.getFile().findReferenceAt(offset - 1);
      assertTrue(ref instanceof PsiJavaCodeReferenceElement);

      ImportClassFix fix = createImportFix((PsiJavaCodeReferenceElement)ref);
      ImportClassFixBase.Result result = fix.doFix(fixture.getEditor(), true, false, true);
      assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, result);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      fixture.getEditor().getCaretModel().moveToOffset(offset - 1);
      fix = createImportFix((PsiJavaCodeReferenceElement)ref);
      result = fix.doFix(fixture.getEditor(), true, false, true);
      assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      assertThat(waitHighlightingSurviveCancellations()).isEmpty();
    });
  }

  @Test
  public void testAutoImportCaretLocationNotImportIfResolved() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
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

      Editor editor = fixture.getEditor();
      PsiFile psiFile = fixture.getFile();
      ThrowableComputable<BooleanSupplier, RuntimeException> computable = () ->
        new JavaReferenceImporter()
          .computeAutoImportAtOffset(editor, psiFile, editor.getCaretModel().getOffset(), true);

      BooleanSupplier supplier = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        return ReadAction.compute(computable);
      }).get();
      assertNull(supplier);
    });
  }

  private static ImportClassFix createImportFix(PsiJavaCodeReferenceElement ref) throws InterruptedException, ExecutionException {
    return ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> new ImportClassFix(ref))).get();
  }

  @Test
  public void testAutoImportCaretLocation2() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      String text = "class X { <caret>ArrayList c = null; }";
      configureByText(text);
      fixture.type(" ");
      fixture.backspace();

      assertThat(waitHighlightingSurviveCancellations()).hasSize(1);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      int offset = fixture.getEditor().getCaretModel().getOffset();
      PsiReference ref = fixture.getFile().findReferenceAt(offset);
      assertTrue(ref instanceof PsiJavaCodeReferenceElement);

      ImportClassFixBase.Result result = createImportFix((PsiJavaCodeReferenceElement)ref).doFix(fixture.getEditor(), true, false, true);
      assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      assertThat(waitHighlightingSurviveCancellations()).isEmpty();
    });
  }

  @Test
  public void testAutoImportWorksWhenITypeSpaceAfterClassName() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "class S { ArrayList<caret> }";
      configureByText(text);

      waitHighlightingSurviveCancellations();
      //caret is too close
      assertNoImportsAdded();

      fixture.type(" ");

      PsiJavaCodeReferenceElement element =
        (PsiJavaCodeReferenceElement)fixture.getFile().findReferenceAt(fixture.getEditor().getCaretModel().getOffset() - 2);
      ImportClassFix fix = createImportFix(element);
      ImportClassFixBase.Result result = fix.doFix(fixture.getEditor(), false, false, true);
      assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);

      assertOneImportAdded("java.util.ArrayList");
    });
  }

  @Test
  public void testAutoImportHintIsShownEvenForNonErrorHighlightInfo() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      String text = """
        class S { XXX456 x; // for resolve
          //showme<caret>
        }
        """;
      MyHintInspection tool = new MyHintInspection();
      fixture.enableInspectionTool(tool);
      SHOWN.set(false);
      configureByText(text);
      fixture.type(" xxx"); // make undoable to enable showing autoimports
      waitHighlightingSurviveCancellations();
      assertTrue(SHOWN.get());
    });
  }

  @Test
  public void testAutoImportHintIsNotShownAfterEscapePressed() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      String text = """
        class S { XXX456 x; // for resolve
          //showme<caret>
        }
        """;
      MyHintInspection tool = new MyHintInspection();
      fixture.enableInspectionTool(tool);
      SHOWN.set(false);
      configureByText(text);
      fixture.type(" xxx"); // make undoable to enable showing autoimports
      waitHighlightingSurviveCancellations();
      assertTrue(SHOWN.get());
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      fixture.getEditor().getSelectionModel().setSelection(0,null, 1); // to enable escape
      fixture.executeAction(IdeActions.ACTION_EDITOR_ESCAPE);
      SHOWN.set(false);
      waitHighlightingSurviveCancellations();
      assertFalse(SHOWN.get());
    });
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


  @Test
  public void testAutoImportAfterUncomment() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      Project project = fixture.getProject();
      assertThat(JavaPsiFacade.getInstance(project).findClasses("java.util.ArrayList", GlobalSearchScope.allScope(project))).hasSize(1);
      assertThat(JavaPsiFacade.getInstance(project).findClasses("java.util.HashMap", GlobalSearchScope.allScope(project))).hasSize(1);
      @Language("JAVA")
      @NonNls String text = "class S { /*ArrayList l; HashMap h; <caret>*/ }";
      configureByText(text);

      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      waitHighlightingSurviveCancellations();

      assertNoImportsAdded();

      EditorTestUtil.executeAction(fixture.getEditor(), IdeActions.ACTION_COMMENT_BLOCK);

      waitHighlightingSurviveCancellations();
      waitForAutoOptimizeImports();
      assertThat(waitHighlightingSurviveCancellations()).isEmpty();

      assertThat(((PsiJavaFile)fixture.getFile()).getImportList().getAllImportStatements()).hasSize(2);
    });
  }

  @RequiresEdt
  private @NotNull Editor createSaveAndOpenFile(@NotNull String relativePath, @NotNull String fileText) throws IOException {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    File tempFile = new File(fixture.createTempVirtualDir().getPath(), relativePath);
    tempFile.getParentFile().mkdirs();
    tempFile.createNewFile();
    VirtualFile vFile = StandardFileSystems.local().refreshAndFindFileByPath(tempFile.getAbsolutePath());
    assert vFile != null;
    WriteAction.runAndWait(() -> {
      vFile.setCharset(StandardCharsets.UTF_8);
      VfsUtil.saveText(vFile, fileText);
    });

    PsiTestUtil.addSourceRoot(fixture.getModule(), vFile.getParent());

    fixture.configureByExistingFile(vFile);
    return fixture.getEditor();
  }

  @Test
  public void testUnresolvedReferenceQuickFixMustReappearAfterTheClassUnderQuestionIsCreated() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      Project project = fixture.getProject();
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
      JavaCodeStyleSettings javaCodeStyleSettings = CodeStyle.getSettings(fixture.getFile()).getCustomSettings(JavaCodeStyleSettings.class);
      javaCodeStyleSettings.INSERT_INNER_CLASS_IMPORTS = true;

      List<HighlightInfo> errors = waitHighlightingSurviveCancellations();
      assertThat(errors).hasSize(1);
      HighlightInfo error = errors.getFirst();
      assertEquals("Cannot resolve symbol 'SomeOtherMethodClass12'", error.getDescription());

      assertNoImportsAdded();

      int offset = otherText.indexOf("//");//before //
      otherEditor.getCaretModel().moveToOffset(offset);
      @Language("JAVA")
      String toType = "public static class SomeOtherMethodClass12 {}";

      //for (int i = 0; i < toType.length(); i++) {
      //  char c = toType.charAt(i);
      //  EditorTestUtil.performTypingAction(otherEditor, c);
      //}
      WriteCommandAction.runWriteCommandAction(project, ()->otherEditor.getDocument().insertString(offset, toType)); // typing sometimes does excessive overtyping and messes the text

      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      assertNotNull(PsiDocumentManager.getInstance(project).getPsiFile(otherEditor.getDocument()));
      assertTrue(PsiShortNamesCache.getInstance(project).getClassesByName("SomeOtherMethodClass12", GlobalSearchScope.allScope(project)).length != 0);
      assertNotNull(JavaPsiFacade.getInstance(project).findClass("x.OtherClass.SomeOtherMethodClass12", GlobalSearchScope.allScope(project)));
      waitHighlightingSurviveCancellations();
      waitForAutoOptimizeImports();
      UIUtil.dispatchAllInvocationEvents(); // make the auto-import modifications
      assertThat(waitHighlightingSurviveCancellations()).isEmpty();

      PsiReference ref = fixture.getFile().findReferenceAt(fixture.getFile().getText().indexOf("SomeOtherMethodClass12"));
      assertNotNull(ReadAction.nonBlocking(()->ref.resolve()).submit(AppExecutorUtil.getAppExecutorService()).get());

      assertOneImportAdded("x.OtherClass.SomeOtherMethodClass12");
    });
  }

  @Test
  public void testEnsureOptimizeImportsWhenInspectionReportsErrors() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "import java.util.List; class S { } <caret>";
      configureByText(text);
      //ensure error will be provided by a local inspection
      NewClassNamingConventionInspection tool = new MyNewClassNamingConventionInspection();
      tool.setEnabled(true, ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
      fixture.enableInspectionTool(tool);

      CodeInsightWorkspaceSettings.getInstance(fixture.getProject()).setOptimizeImportsOnTheFly(true, fixture.getTestRootDisposable());

      //error corresponding to too short class name
      assertThat(waitHighlightingSurviveCancellations()).hasSize(1);

      assertOneImportAdded("java.util.List");

      fixture.type("/* */");
      waitHighlightingSurviveCancellations();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      assertNoImportsAdded();
    });
  }

  @Test
  public void testAutoImportWorks() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls final String text = "class S { JFrame x; <caret> }";
      configureByText(text);
      boolean isInContent = true;
      assertFalse(DaemonListeners.canChangeFileSilently(fixture.getFile(), isInContent, ThreeState.UNSURE));


      waitHighlightingSurviveCancellations();
      assertFalse(DaemonListeners.canChangeFileSilently(fixture.getFile(), isInContent, ThreeState.UNSURE));

      fixture.type(" ");
      assertTrue(DaemonListeners.canChangeFileSilently(fixture.getFile(), isInContent, ThreeState.UNSURE));

      UndoManager.getInstance(fixture.getProject()).undo(TextEditorProvider.getInstance().getTextEditor(fixture.getEditor()));

      assertFalse(DaemonListeners.canChangeFileSilently(fixture.getFile(), isInContent, ThreeState.UNSURE));
    });
  }


  @Test
  public void testAutoImportOfGenericReference() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls final String text = "class S {{ new ArrayList<caret><String> }}";
      configureByText(text);
      EditorTestUtil.setEditorVisibleSize(fixture.getEditor(), 1000, 1000); // make sure editor is visible - auto-import works only for visible area
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      fixture.type(" ");
      fixture.backspace();

      waitHighlightingSurviveCancellations();
      //caret is too close
      assertNoImportsAdded();

      fixture.caretRight();

      waitHighlightingSurviveCancellations();

      assertOneImportAdded("java.util.ArrayList");
    });
  }

  @RequiresEdt
  private void assertOneImportAdded(String s) throws Exception {
    waitForAutoOptimizeImports();
    PsiImportStatementBase[] statements = ((PsiJavaFile)daemonFixture.get().getFile()).getImportList().getAllImportStatements();
    assertThat(statements).hasSize(1);
    PsiImportStatementBase importStatement = statements[0];
    PsiElement resolved = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> importStatement.resolve())).get();
    assertTrue(resolved instanceof PsiClass);
    assertEquals(s, ((PsiClass)resolved).getQualifiedName());
  }

  @RequiresEdt
  private void assertNoImportsAdded() {
    waitForAutoOptimizeImports();
    assertThat(((PsiJavaFile)daemonFixture.get().getFile()).getImportList().getAllImportStatements()).isEmpty();
  }

  @Test
  public void testAutoOptimizeUnresolvedImports() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "import xxx.yyy; class S { } <caret> ";
      configureByText(text);

      CodeInsightWorkspaceSettings.getInstance(fixture.getProject()).setOptimizeImportsOnTheFly(true, fixture.getTestRootDisposable());
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      List<HighlightInfo> errs = waitHighlightingSurviveCancellations();
      waitForAutoOptimizeImports();

      //error in import list
      assertThat(errs).hasSize(1);

      assertThat(((PsiJavaFile)fixture.getFile()).getImportList().getAllImportStatements()).hasSize(1);

      fixture.type("/* */");
      waitHighlightingSurviveCancellations();

      assertNoImportsAdded();
    });
  }

  @Test
  public void testUndoAfterOptimizeImportsOnTheFly() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      Project project = fixture.getProject();
      assertNotNull(JavaPsiFacade.getInstance(project).findClass("java.util.Date", GlobalSearchScope.allScope(project)));
      @Language("JAVA") String text = """
        import java.util.*;

        final class UndoTestJava {
            static void main() {
                <caret>Date d = new Date();// Comment out this line then try undo
            }
        }
        """;
      configureByText(text);
      CodeInsightWorkspaceSettings.getInstance(project).setOptimizeImportsOnTheFly(true, fixture.getTestRootDisposable());
      EditorTestUtil.executeAction(fixture.getEditor(), IdeActions.ACTION_COMMENT_LINE);
      waitHighlightingSurviveCancellations();
      waitForAutoOptimizeImports();
      List<HighlightInfo> errors = waitHighlightingSurviveCancellations();
      assertThat(errors).isEmpty();
      assertEquals("""
                     final class UndoTestJava {
                         static void main() {
                     //        Date d = new Date();// Comment out this line then try undo
                         }
                     }
                     """, fixture.getFile().getText());
      EditorTestUtil.executeAction(fixture.getEditor(), IdeActions.ACTION_UNDO);
      waitHighlightingSurviveCancellations();
      waitForAutoOptimizeImports();
      errors = waitHighlightingSurviveCancellations();
      assertThat(errors).isEmpty();
      assertEquals("""
                     import java.util.*;

                     final class UndoTestJava {
                         static void main() {
                             Date d = new Date();// Comment out this line then try undo
                         }
                     }
                     """, fixture.getFile().getText());
    });
  }

  @RequiresEdt
  private void waitForAutoOptimizeImports() {
    Project project = daemonFixture.get().getProject();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    TestDaemonCodeAnalyzerImpl.waitWhilePumping(ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        OptimizeImportRestarter.getInstance(project).waitForScheduledOptimizeImportRequestsInTests();
      }
      catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }));
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  @Test
  public void testUnambiguousImportMustBeInsertedEvenWhenShowImportPopupIsOff() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
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
        fixture.type(" ");
        fixture.backspace();
        waitHighlightingSurviveCancellations();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertOneImportAdded("java.util.ArrayList");
      }
      finally {
        DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(importHintEnabled);
      }
    });
  }

  @Test
  public void testAutoOptimizeDoesntSuddenlyRemoveImportsDuringTyping() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "package x; " +
                            "import java.util.ArrayList; " +
                            "class S {{ <caret> ArrayList l;\n" +
                            "}}";
      configureByText(text);

      CodeInsightWorkspaceSettings.getInstance(fixture.getProject()).setOptimizeImportsOnTheFly(true, fixture.getTestRootDisposable());

      List<HighlightInfo> errs = waitHighlightingSurviveCancellations();

      assertThat(errs).isEmpty();

      fixture.type("/* ");
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      errs = waitHighlightingSurviveCancellations();
      assertThat(errs).isNotEmpty();
      assertOneImportAdded("java.util.ArrayList");
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      fixture.type(" */ ");
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      errs = waitHighlightingSurviveCancellations();
      assertThat(errs).isEmpty();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      assertOneImportAdded("java.util.ArrayList");
    });
  }

  @Test
  public void testAutoInsertImportForInnerClass() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
      configureByText(text);

      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;

      List<HighlightInfo> errs = waitHighlightingSurviveCancellations();
      assertThat(errs).hasSize(1);

      assertNoImportsAdded();
      fixture.type("/* */");
      waitHighlightingSurviveCancellations();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      assertNoImportsAdded();
    });
  }

  @Test
  public void testAutoInsertImportForInnerClassAllowInnerClassImports() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
      configureByText(text);

      JavaCodeStyleSettings javaCodeStyleSettings = CodeStyle.getSettings(fixture.getFile()).getCustomSettings(JavaCodeStyleSettings.class);
      javaCodeStyleSettings.INSERT_INNER_CLASS_IMPORTS = true;
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      fixture.type(" ");
      waitHighlightingSurviveCancellations();

      assertOneImportAdded("java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock");
    });
  }

  @Test
  public void testAutoImportSkipsClassReferenceInMethodPosition() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text =
        "package x; import java.util.HashMap; class S { HashMap<String,String> f(){ return  Hash<caret>Map <String, String >();} }  ";
      configureByText(text);

      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;

      List<HighlightInfo> errs = waitHighlightingSurviveCancellations();
      assertTrue(errs.size() > 1);

      PsiJavaFile javaFile = (PsiJavaFile)fixture.getFile();
      assertOneImportAdded("java.util.HashMap");

      PsiReference ref = javaFile.findReferenceAt(fixture.getEditor().getCaretModel().getOffset());
      ImportClassFix fix = createImportFix((PsiJavaCodeReferenceElement)ref);
      assertFalse(fix.isAvailable(fixture.getProject(), fixture.getEditor(), fixture.getFile()));
    });
  }

  @Test
  public void testAutoImportDoNotBreakCode() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "package x; class S {{ S.<caret>\n Runnable r; }}";
      configureByText(text);

      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      CodeInsightWorkspaceSettings.getInstance(fixture.getProject()).setOptimizeImportsOnTheFly(true, fixture.getTestRootDisposable());

      assertThat(waitHighlightingSurviveCancellations()).hasSize(1);
    });
  }

  @Test
  public void testAutoImportIgnoresUnresolvedImportReferences() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls String text = "package x; import xxx.yyy.ArrayList; /** @noinspection ClassInitializerMayBeStatic*/ class S {{ ArrayList<caret> r; }}";
      configureByText(text);

      PsiJavaFile javaFile = (PsiJavaFile)fixture.getFile();
      PsiReference ref = javaFile.findReferenceAt(fixture.getEditor().getCaretModel().getOffset() - 1);
      ImportClassFix fix = createImportFix((PsiJavaCodeReferenceElement)ref);
      //explicitly available
      assertFalse(fix.isAvailable(fixture.getProject(), fixture.getEditor(), fixture.getFile()));
      //hint is not available
      assertFalse(fix.showHint(fixture.getEditor()));
    });
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

  @Test
  public void testAutoImportMustNotRunResolveInEDT() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      @Language("JAVA")
      @NonNls final String text = "class S {{ new ArrayList<<caret>String> }}";
      configureByText(text);
      fixture.type(" ");
      fixture.backspace(); // make file undoable

      EditorTestUtil.setEditorVisibleSize(fixture.getEditor(), 1000, 1000); // make sure editor is visible - auto-import works only for visible area
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      ThreadingAssertions.assertEventDispatchThread();
      waitHighlightingSurviveCancellations();
      assertOneImportAdded("java.util.ArrayList");
    });
  }

  public static boolean isFromJavaCodeReferenceElementResolve() {
    Throwable currentStack = new Throwable();
    return ContainerUtil.exists(currentStack.getStackTrace(),
       stackElement -> stackElement.getClassName().equals(PsiImplUtil.class.getName())
                       && stackElement.getMethodName().equals("multiResolveImpl"));
  }

  @Test
  public void testImportHintsMustBeComputedForAllUnresolvedReferencesInVisibleAreaToBeAbleToShowPopups() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
      ThreadingAssertions.assertEventDispatchThread();
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
      @Language("JAVA")
      @NonNls final String text = "class S {{ \n" +
                                  "new ArrayList();\n".repeat(1000) +
                                  " }}";
      configureByText(text);

      Editor editor = fixture.getEditor();
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(500, 10));
      EditorTestUtil.setEditorVisibleSize(editor, 100, 100); // make sure editor is visible - auto-import works only for visible area
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      EditorTestUtil.setEditorVisibleSize(editor, 100, 100); // make sure editor is visible - auto-import works only for visible area
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
      TextRange visibleRange = editor.calculateVisibleRange();
      assertTrue(visibleRange.getStartOffset() > 5000 && visibleRange.getEndOffset() < 10_000, visibleRange.toString()); // sanity check that visible range has been indeed changed

      List<HighlightInfo> errors = ContainerUtil.sorted(waitHighlightingSurviveCancellations(), Segment.BY_START_OFFSET_THEN_END_OFFSET);
      assertThat(errors).hasSize(1000);
      LazyQuickFixUpdaterImpl updater = (LazyQuickFixUpdaterImpl)LazyQuickFixUpdater.getInstance(fixture.getProject());
      long deadline = System.currentTimeMillis() + 60_000;
      for (int i = 0; i < errors.size(); i++) {
        HighlightInfo error = errors.get(i);
        if (visibleRange.contains(error)) { // we care only for visible errors; invisible ones may or may not be computed
          updater.waitForBackgroundJobIfStartedInTests(fixture.getProject(), editor.getDocument(), error, deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
          if (!error.hasHint()) {
            List<HintAction> hints = ShowAutoImportPass.extractHints(error);
            String message = error + ": " + i + " hasHints: "+error.hasHint() + "; hints:" + hints + "; visibleRange:" + visibleRange + "; contains: " + visibleRange.contains(error);
            fail(message);
          }
        }
      }
    });
  }

  @Test
  public void testImportHintMustStillBeAvailableAfterTypingBeforeTheReference() {
    runTest(() -> {
      ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
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
      Editor editor = fixture.getEditor();

      assertHasImportHintAllOverUnresolvedReference("");
      int offset = editor.getDocument().getText().indexOf("ArrayList");
      for (int i=0; i<10; i++) {
        fixture.getEditor().getCaretModel().moveToOffset(offset+i);
        fixture.type(" ");
        assertHasImportHintAllOverUnresolvedReference(String.valueOf(i));
      }
      for (int i=0; i<10; i++) {
        fixture.getEditor().getCaretModel().moveToOffset(offset+10+i);
        fixture.type("\n");
        assertHasImportHintAllOverUnresolvedReference(String.valueOf(i));
      }
    });
  }

  @RequiresEdt
  private void assertHasImportHintAllOverUnresolvedReference(String message) throws Exception {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    Project project = fixture.getProject();
    Editor editor = fixture.getEditor();
    PsiFile psiFile = fixture.getFile();
    List<HighlightInfo> errors = ContainerUtil.sorted(waitHighlightingSurviveCancellations(), Segment.BY_START_OFFSET_THEN_END_OFFSET);
    IdentifierHighlighterPassFactory.waitForIdentifierHighlighting(editor);
    CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, editor);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();//auto-imports use non-blocking read actions to compute imports
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    assertThat(errors).isNotEmpty();
    HighlightInfo error = errors.getFirst();
    assertEquals("Cannot resolve symbol 'ArrayList'", error.getDescription(), message);
    assertTrue(error.hasHint(), message);
    HighlightInfo.IntentionActionDescriptor errDesc = error.findRegisteredQuickFix((descriptor, _) -> descriptor.getAction().getText().startsWith("Import class") ? descriptor : null);
    assertNotNull(errDesc);
    assertTrue(errDesc.getAction().isAvailable(project, editor, psiFile), message);
    for (int i = error.getActualStartOffset(); i < error.getActualEndOffset(); i++) {
      editor.getCaretModel().moveToOffset(i);
      List<HighlightInfo.IntentionActionDescriptor> errDescriptors = ReadAction.nonBlocking(()->ShowIntentionsPass.getActionsToShow(editor, psiFile).errorFixesToShow).submit(AppExecutorUtil.getAppExecutorService()).get();
      HighlightInfo.IntentionActionDescriptor importDesc = ContainerUtil.find(errDescriptors, descriptor -> descriptor.getAction().getText().startsWith("Import class"));
      assertNotNull(importDesc, message + ": " + i);
      assertTrue(importDesc.getAction().isAvailable(project, editor, psiFile), message);
    }
  }
}
