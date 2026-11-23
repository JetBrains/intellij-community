// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspectionBase;
import com.intellij.codeInspection.miscGenerics.RawUseOfParameterizedTypeInspection;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.debugger.DebugException;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.TestTimeOut;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesFixture.daemonRespondToChangesFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * tests {@link LocalInspectionTool} behaviour during highlighting
 */
@TestApplication
@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
public class DaemonInspectionsRespondToChangesTest {
  private static final Logger LOG = Logger.getInstance(DaemonInspectionsRespondToChangesTest.class);

  private final TestFixture<DaemonRespondToChangesFixture> daemonFixture = daemonRespondToChangesFixture(() -> new LocalInspectionTool[]{
    new FieldCanBeLocalInspection(),
    new RequiredAttributesInspectionBase(),
    new CheckDtdReferencesInspection(),
    new AccessStaticViaInstance(),
  });

  @BeforeEach
  void setUp() {
    PlatformTestUtil.assumeEnoughParallelism();
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.enableInspectionTool(new UnusedDeclarationInspection());
    UndoManager.getInstance(fixture.getProject());
  }

  @Test
  public void testWholeFileInspection() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(DaemonRespondToChangesTest.BASE_PATH + "FieldCanBeLocal.java");
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).hasSize(1);
      assertEquals("Field can be converted to a local variable", infos.getFirst().getDescription());

      fixture.ctrlW();
      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> EditorModificationUtilEx.deleteSelectedText(fixture.getEditor()));
      fixture.type("xxxx");

      infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).isEmpty();

      fixture.ctrlW();
      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> EditorModificationUtilEx.deleteSelectedText(fixture.getEditor()));
      fixture.type("0");

      infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).hasSize(1);
      assertEquals("Field can be converted to a local variable", infos.getFirst().getDescription());
    });
  }

  private abstract static class MyTrackingInspection extends MyInspectionBase {
    private final List<PsiElement> visited = Collections.synchronizedList(new ArrayList<>());

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new PsiElementVisitor() {
        @Override
        public void visitFile(@NotNull PsiFile psiFile) {
          TimeoutUtil.sleep(1000); // make it run longer than LIP
          super.visitFile(psiFile);
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
          visited.add(element);
        }
      };
    }
  }

  static abstract class MyInspectionBase extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return "my inspection: "+getClass().getName();
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return getGroupDisplayName();
    }
  }

  private static class MyWholeInspection extends MyTrackingInspection {
    @Override
    public boolean runForWholeFile() {
      return true;
    }
  }

  @Test
  public void testWholeFileInspectionRestartedOnAllElements() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      MyTrackingInspection tool = registerInspection(new MyWholeInspection());

      @Language("JAVA")
      String text = "class X { void f() { <caret> } }";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).isEmpty();
      int visitedCount = new HashSet<>(tool.visited).size();
      tool.visited.clear();

      fixture.type(" ");  // white space modification

      infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).isEmpty();

      int countAfter = new HashSet<>(tool.visited).size();
      assertTrue(countAfter >= visitedCount, "visitedCount = "+visitedCount+"; countAfter="+countAfter);
    });
  }

  private @NotNull <T extends LocalInspectionTool> T registerInspection(@NotNull T tool) {
    daemonFixture.get().enableInspectionTool(tool);
    return tool;
  }

  @Test
  public void testWholeFileInspectionRestartedEvenIfThereWasAModificationInsideCodeBlockInOtherFile() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      MyTrackingInspection tool = registerInspection(new MyWholeInspection());

      @Language("JAVA")
      String text = "class X { void f() { <caret> } }";
      PsiFile psiFile = fixture.configureByText(JavaFileType.INSTANCE, text);
      VirtualFile dir = ReadAction.computeBlocking(() -> psiFile.getContainingDirectory().getVirtualFile());
      PsiFile otherPsiFile = fixture.createFile(fixture.getModule(), dir, "otherFile.txt", "xxx");
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).isEmpty();
      assertFalse(tool.visited.isEmpty());
      tool.visited.clear();

      Document otherDocument = Objects.requireNonNull(fixture.getDocument(otherPsiFile));
      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> otherDocument.setText("zzz"));
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).isEmpty();

      assertFalse(tool.visited.isEmpty());
      tool.visited.clear();

      //ensure started on another file
      fixture.configureByExistingFile(otherPsiFile.getVirtualFile());
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).isEmpty();

      assertFalse(tool.visited.isEmpty());
    });
  }

  @Test
  public void testInspectionIsRestartedOnPsiCacheDrop() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      MyTrackingInspection tool = registerInspection(new MyTrackingInspection(){});

      @Language("JAVA")
      String text = "class X { void f() { <caret> } }";
      PsiFile psiFile = fixture.configureByText(JavaFileType.INSTANCE, text);
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile));
      tool.visited.clear();

      EdtTestUtil.runInEdtAndWait(() -> {
        PsiManager.getInstance(fixture.getProject()).dropPsiCaches();
        testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile);
      });
      assertThat(tool.visited).isNotEmpty();
    });
  }

  @Test
  public void testLIPGetAllParentsAfterCodeBlockModification() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            int f;
            public void me() {
                //<caret>
            }
        }""";

      List<PsiElement> visitedElements = Collections.synchronizedList(new ArrayList<>());
      class MyVisitor extends PsiElementVisitor {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          visitedElements.add(element);
        }
      }
      LocalInspectionTool tool = new MyInspectionBase() {
        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new MyVisitor();
        }
      };
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        Disposer.register(fixture.getTestRootDisposable(), () -> fixture.disableInspectionTool(tool.getShortName()));
        fixture.enableInspectionTool(tool);

        assertThat(fixture.getTestDaemonCodeAnalyzer().waitHighlighting(fixture.getFile(), HighlightSeverity.ERROR)).isEmpty();
      });

      PsiFile psiFile = fixture.getFile();
      List<PsiElement> allPsi = ReadAction.computeBlocking(() -> CollectHighlightsUtil.getElementsInRange(psiFile, 0, psiFile.getTextLength()));
      assertEquals(new HashSet<>(allPsi), new HashSet<>(visitedElements));

      // inside code block modification
      visitedElements.clear();
      fixture.backspace();
      fixture.backspace();

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      List<PsiElement> methodAndParents = ReadAction.computeBlocking(() -> {
        PsiMethod method = ((PsiJavaFile)psiFile).getClasses()[0].getMethods()[0];
        return CollectHighlightsUtil.getElementsInRange(psiFile, method.getTextRange().getStartOffset(), method.getTextRange().getEndOffset(), true);
      });
      assertEquals(new HashSet<>(methodAndParents), new HashSet<>(visitedElements));
    });
  }

  @Test
  public void testDumbQuickFixIsNoLongerVisibleAfterApplied() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.getTestDaemonCodeAnalyzer().mustWaitForSmartModeByDefault(false);
      registerInspection(new FindElseBranchInspection());

      @Language("JAVA")
      String text = "class X { void f() { i<caret>f (this == null) {} else return; } }";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlightingSurviveCancellations(HighlightSeverity.ERROR)).isEmpty();
      List<IntentionAction> fixes = EdtTestUtil.runInEdtAndGet(() -> findStupidFixes(fixture));
      assertThat(fixes).hasSize(1);
      IntentionAction fix = fixes.getFirst();
      EdtTestUtil.runInEdtAndWait(() -> {
        fix.invoke(fixture.getProject(), fixture.getEditor(), fixture.getFile());

        assertThat(findStupidFixes(fixture)).isEmpty();
      });

      assertThat(fixture.waitHighlightingSurviveCancellations(HighlightSeverity.ERROR)).isEmpty();
      assertThat(EdtTestUtil.runInEdtAndGet(() -> findStupidFixes(fixture))).isEmpty();
    });
  }

  @Unmodifiable
  private static @NotNull List<IntentionAction> findStupidFixes(@NotNull DaemonRespondToChangesFixture fixture) {
    return ContainerUtil.filter(CodeInsightTestFixtureImpl.getAvailableIntentions(fixture.getEditor(), fixture.getFile()), f -> f.getFamilyName()
      .equals(new FindElseBranchInspection.StupidQuickFixWhichDoesntCheckItsOwnApplicability().getFamilyName()));
  }

  private static class FindElseBranchInspection extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return "danuna";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitIfStatement(@NotNull PsiIfStatement statement) {
          if (statement.getElseBranch() != null) {
            PsiKeyword keyword = (PsiKeyword)statement.getChildren()[0];
            holder.registerProblem(keyword, "Dododo", new StupidQuickFixWhichDoesntCheckItsOwnApplicability());
          }
        }
      };
    }
    private static class StupidQuickFixWhichDoesntCheckItsOwnApplicability implements LocalQuickFix {
      @Nls
      @NotNull
      @Override
      public String getName() {
        return "danu";
      }

      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return getName();
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        WriteCommandAction.runWriteCommandAction(project, () -> ((PsiIfStatement)descriptor.getPsiElement().getParent()).getElseBranch().delete());
      }
    }
  }

  private static final AtomicInteger toSleepMs = new AtomicInteger(0);
  private static final String SWEARING = "No swearing";

  private void checkSwearingHighlightIsVisibleImmediately() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    @Language("JAVA")
    String text = """
      class X /* */ {
        int foo(Object param) {//XXX
          return 0;
        }/* */
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByText(JavaFileType.INSTANCE, text);
      ((EditorImpl)fixture.getEditor()).getScrollPane().getViewport().setSize(1000, 1000);
      assertEquals(fixture.getFile().getTextRange(), fixture.getEditor().calculateVisibleRange());

      toSleepMs.set(1_000_000);

      MarkupModel markupModel = DocumentMarkupModel.forDocument(fixture.getEditor().getDocument(), fixture.getProject(), true);
      TestTimeOut n = TestTimeOut.setTimeout(100, TimeUnit.SECONDS);
      AtomicInteger called = new AtomicInteger();
      Runnable checkHighlighted = () -> {
        called.incrementAndGet();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        long highlighted = Arrays.stream(markupModel.getAllHighlighters())
          .filter(h->h.isValid())
          .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
          .filter(Objects::nonNull)
          .filter(info -> SWEARING.equals(info.getDescription()))
          .count();
        if (highlighted != 0) {
          toSleepMs.set(0);
          throw new DebugException(); // sorry for that, had to differentiate from failure
        }
        if (n.timedOut()) {
          toSleepMs.set(0);
          throw new RuntimeException(new TimeoutException(ThreadDumper.dumpThreadsToString()));
        }
      };
      try {
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(fixture.getProject());
        PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
        long start = System.currentTimeMillis();
        fixture.getTestDaemonCodeAnalyzer().waitForDaemonToFinish(fixture.getFile(), checkHighlighted);
        List<RangeHighlighter> errors = ContainerUtil.filter(markupModel.getAllHighlighters(), highlighter -> highlighter.isValid() && HighlightInfo.fromRangeHighlighter(highlighter) != null && HighlightInfo.fromRangeHighlighter(highlighter).getSeverity() == HighlightSeverity.ERROR);
        long elapsed = System.currentTimeMillis() - start;

        fail("should have been interrupted. toSleepMs: " + toSleepMs + "; highlights: " + errors + "; called: " + called+"; highlighted in "+elapsed+"ms");
      }
      catch (DebugException ignored) {
      }
    });
  }

  @Test
  public void testAddInspectionProblemToProblemHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      registerInspection(new MySwearingInspection());
      checkSwearingHighlightIsVisibleImmediately();
    });
  }

  // produces an error problem for "XXX" comment very fast, and then very slowly inspects the rest of the file
  private static class MySwearingInspection extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return "sweardanuna";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitComment(@NotNull PsiComment element) {
          if (element.getText().equals("//XXX")) {
            holder.registerProblem(element, SWEARING);
          }
        }

        @Override
        public void visitFile(@NotNull PsiFile psiFile) {
          // to bail out immediately by modifying toSleepMs in the other thread
          sleepInterruptibly(toSleepMs);
        }
      };
    }
  }

  @Test
  public void testLocalInspectionPassMustRunFastFertileInspectionsFirstToReduceLatency() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            int f;
            public void me() {
                //
            }
         void foo//<caret>
        (){}}""";

      AtomicReference<String> diagnosticText = new AtomicReference<>("1st run");
      AtomicInteger stallMs = new AtomicInteger();
      // highlight fields fast, stall every other element
      LocalInspectionTool tool = new MyInspectionBase() {
        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
              holder.registerProblem(field.getNameIdentifier(), diagnosticText.get());
            }

            @Override
            public void visitElement(@NotNull PsiElement element) {
              // stall every other element to exacerbate latency problems if the order is wrong
              sleepInterruptibly(stallMs);
            }
          };
        }
      };
      Project project = fixture.getProject();
      PsiField field = EdtTestUtil.runInEdtAndGet(() -> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        PsiField firstField = ((PsiJavaFile)fixture.getFile()).getClasses()[0].getFields()[0];
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way
        UIUtil.markAsFocused(fixture.getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
        SeverityRegistrar.getSeverityRegistrar(project); //preload inspection profile

        Disposer.register(fixture.getTestRootDisposable(), () -> fixture.disableInspectionTool(tool.getShortName()));
        for (Tools tools : ProjectInspectionProfileManager.getInstance(project).getCurrentProfile().getAllEnabledInspectionTools(project)) {
          fixture.disableInspectionTool(tools.getTool().getShortName());
        }
        fixture.enableInspectionTool(tool);

        List<HighlightInfo> infos = fixture.getTestDaemonCodeAnalyzer().waitHighlighting(fixture.getFile(), HighlightSeverity.WARNING);
        assertThat(infos).hasSize(1);
        assertEquals(diagnosticText.get(), infos.getFirst().getDescription());
        return firstField;
      });

      diagnosticText.set("Aha, field, finally!");
      stallMs.set(TestDaemonCodeAnalyzerImpl.WAIT_DAEMON_FOR_FINISH_TIMEOUT_MS + 10_000);
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.backspace();
        fixture.backspace();
        fixture.type("blah");
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way
        PsiField field2 = ((PsiJavaFile)fixture.getFile()).getClasses()[0].getFields()[0];
        assertSame(field, field2);// assert the reparse didn't touch the field, in order to check the "fertile optimization"
        TextRange range = field.getNameIdentifier().getTextRange();
        MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(fixture.getEditor().getDocument(), project, true);

        // now when the LIP restarted, we should get back our inspection result very fast, despite very slow processing of every other element
        TestTimeOut t = TestTimeOut.setTimeout(TestDaemonCodeAnalyzerImpl.WAIT_DAEMON_FOR_FINISH_TIMEOUT_MS-5_000, TimeUnit.MILLISECONDS);

        fixture.getTestDaemonCodeAnalyzer().waitForDaemonToFinish(fixture.getFile(), () -> {
          boolean found = !DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, range.getStartOffset(), range.getEndOffset(), info -> !diagnosticText.get().equals(info.getDescription()));
          if (found) {
            stallMs.set(0);
          }
          if (t.isTimedOut()) {
            LOG.debug("all highlighters:\n"+DaemonRespondToChangesTest.renderHighlighters(List.of(model.getAllHighlighters()))
                      +"\nfound:"+!DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, range.getStartOffset(), range.getEndOffset(), info -> !diagnosticText.get().equals(info.getDescription()))
            );
            stallMs.set(0);
            fail("time out waiting for fertile inspection");
          }
        });
      });
    });
  }

  private static void sleepInterruptibly(@NotNull AtomicInteger stallMs) {
    for (int i = 0; i < stallMs.get(); i+=10) {
      TimeoutUtil.sleep(10); // to make wait interruptible
    }
  }

  private static volatile TextRange expectedVisibleRange;
  private static class MyVisibleRangeInspection extends MyTrackingInspection {
    @Override
    public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
      TextRange priorityRange = session.getPriorityRange();
      assertEquals(expectedVisibleRange, priorityRange);
      expectedVisibleRange = null;
    }
  }

  private static final String wordToAnnotate = "annotate_here";

  @Test
  public void testLocalInspectionMustReceiveCorrectVisibleRangeViaItsHighlightingSession() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      registerInspection(new MyVisibleRangeInspection());

      String text = "blah blah\n".repeat(1000) +
                    "<caret>" + wordToAnnotate +
                    "\n" +
                    "balh blah\n".repeat(1000);
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.configureByText(PlainTextFileType.INSTANCE, text);
        EditorImpl editor = (EditorImpl)fixture.getEditor();
        editor.getScrollPane().getViewport().setSize(1000, 1000);
        DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        Point2D caretVisualPoint = editor.offsetToPoint2D(editor.getCaretModel().getOffset());
        editor.getScrollPane().getViewport().setViewPosition(new Point((int)caretVisualPoint.getX(), (int)caretVisualPoint.getY()));
        editor.getScrollPane().getViewport().setExtentSize(new Dimension(100, editor.getPreferredHeight() - (int)caretVisualPoint.getY()));
        ProperTextRange visibleRange = editor.calculateVisibleRange();
        assertTrue(visibleRange.getStartOffset() > 0, visibleRange.toString());
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        expectedVisibleRange = visibleRange;
        fixture.getTestDaemonCodeAnalyzer().waitHighlighting(fixture.getFile(), HighlightSeverity.INFORMATION);
      });
      assertNull(expectedVisibleRange); // check the inspection was run
      EdtTestUtil.runInEdtAndWait(() -> {
        EditorImpl editor = (EditorImpl)fixture.getEditor();
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible(editor);
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        expectedVisibleRange = new TextRange(0, editor.getDocument().getTextLength());
        fixture.getTestDaemonCodeAnalyzer().waitHighlighting(fixture.getFile(), HighlightSeverity.INFORMATION);
      });
      assertNull(expectedVisibleRange); // check the inspection was run
    });
  }

  // add file-level "blah" for each identifier containing "XXX"
  private static class MyFileLevelInspection extends MyInspectionBase {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitIdentifier(@NotNull PsiIdentifier identifier) {
          if (identifier.getText().contains("XXX")) {
            holder.registerProblem(identifier.getContainingFile(), "Blah", ProblemHighlightType.WARNING);
          }
        }
      };
    }
  }

  private static @NotNull List<HighlightInfo> getFileLevelHighlights(@NotNull DaemonRespondToChangesFixture fixture) {
    PsiFile psiFile = fixture.getFile();
    return EdtTestUtil.runInEdtAndGet(() -> fixture.getDaemonCodeAnalyzer().getFileLevelHighlights(fixture.getProject(), psiFile));
  }

  @Test
  public void testFileLevelHighlightingDoesNotDuplicateOnTypingInsideSmallRange() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      registerInspection(new MyFileLevelInspection());
      @Language("JAVA")
      String text = """
        class X {
          void foo() {
            int XXX<caret>;
          }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertThat(getFileLevelHighlights(fixture)).hasSize(1);

      fixture.type('2');
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertThat(getFileLevelHighlights(fixture)).hasSize(1);
    });
  }

  @Test
  public void testFileLevelHighlightingDoesNotDuplicateOnTypingOrOnFileCloseReopen() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      registerInspection(new MyFileLevelInspection());
      @Language("JAVA")
      String text = """
        class X {
          int XXX<caret>;
        }""";
      PsiFile psiFile = fixture.configureByText(JavaFileType.INSTANCE, text);
      VirtualFile virtualFile = psiFile.getVirtualFile();

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertThat(getFileLevelHighlights(fixture)).hasSize(1);
      EdtTestUtil.runInEdtAndWait(() -> FileEditorManager.getInstance(fixture.getProject()).closeFile(virtualFile));

      for (int i=0; i<100; i++) {
        fixture.configureByExistingFile(virtualFile);
        EdtTestUtil.runInEdtAndWait(() -> {
          fixture.getEditor().getCaretModel().moveToOffset(fixture.getEditor().getDocument().getText().indexOf("XXX") + "XXX".length());
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        });
        fixture.type('2');
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
        assertThat(getFileLevelHighlights(fixture)).hasSize(1);
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
        fixture.backspace();
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
        assertThat(getFileLevelHighlights(fixture)).hasSize(1);

        LOG.debug("i = " + i);

        EdtTestUtil.runInEdtAndWait(() -> FileEditorManager.getInstance(fixture.getProject()).closeFile(virtualFile));
      }
    });
  }

  @Test
  public void testFileLevelWithEverChangingDescriptionMustUpdateOnTyping() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      // add file-level "blah" if there are identifiers containing "xxx"
      class XXXIdentifierFileLevelInspection extends MyInspectionBase {
        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new JavaElementVisitor() {
            @Override
            public void visitIdentifier(@NotNull PsiIdentifier identifier) {
              if (identifier.getText().contains("xxx")) {
                holder.registerProblem(identifier.getContainingFile(),"xxx: "+identifier.getText(), ProblemHighlightType.WARNING);
              }
            }
          };
        }
      }

      registerInspection(new XXXIdentifierFileLevelInspection());
      @Language("JAVA")
      String text = """
        class X {
          void foo() {
            int xxx<caret>;
          }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      List<HighlightInfo> infos = getFileLevelHighlights(fixture);
      assertThat(infos).hasSize(1);
      assertEquals("xxx: xxx", infos.getFirst().getDescription());

      fixture.type('2');
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      infos = getFileLevelHighlights(fixture);
      assertThat(infos).hasSize(1);
      assertEquals("xxx: xxx2", infos.getFirst().getDescription());

      fixture.type('y');
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      infos = getFileLevelHighlights(fixture);
      assertThat(infos).hasSize(1);
      assertEquals("xxx: xxx2y", infos.getFirst().getDescription());
    });
  }

  @Test
  public void testInspectionMustRemoveItsObsoleteHighlightsImmediatelyAfterFinished() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            // xxx
            int f;<caret>
        }""";

      AtomicReference<String> fieldWarningText = new AtomicReference<>("1st run");
      AtomicInteger stallSlowToolMs = new AtomicInteger(0);
      AtomicBoolean slowToolFinished = new AtomicBoolean();
      LocalInspectionTool slowTool = new MyInspectionBase() {
        @Override
        public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
          // invoke later because we are checking this flag in EDT below, and
          // we do not want a race between contextFinishedCallback.accept(context); in inspection thread
          // and querying markup model in EDT
          ApplicationManager.getApplication().invokeLater(() -> {
            //System.out.println("slow finished ");
            slowToolFinished.set(true);
          });
        }
        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
              //System.out.println("slow visit field "+field + Thread.currentThread());
              holder.registerProblem(field.getNameIdentifier(), fieldWarningText.get());
            }

            @Override
            public void visitElement(@NotNull PsiElement element) {
              //System.out.println("slow visit "+element + Thread.currentThread());
              // stall every other element to exacerbate latency problems if the order is wrong
              sleepInterruptibly(stallSlowToolMs);
            }
          };
        }
      };

      AtomicBoolean fastToolFinished = new AtomicBoolean();
      // highlights all "xxx" comments, only when there are no comments after it
      String fastToolText = "Blah";
      LocalInspectionTool fastTool = new MyInspectionBase() {
        @Override
        public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
          // invoke later because we are checking this flag in EDT below, and
          // we do not want a race between contextFinishedCallback.accept(context); in inspection thread
          // and querying markup model in EDT
          //System.out.println("fast about to finished ");
          ApplicationManager.getApplication().invokeLater(() -> {
            //System.out.println("fast finished ");
            fastToolFinished.set(true);
          });
        }
        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new JavaElementVisitor() {
            @Override
            public void visitComment(@NotNull PsiComment comment) {
              //System.out.println("fast visit comment "+comment + Thread.currentThread());
              if (comment.getText().contains("xxx") && !comment.getContainingFile().getText().substring(comment.getTextOffset()+2).contains("//")) {
                holder.registerProblem(comment, fastToolText, ProblemHighlightType.WARNING);
              }
            }

            @Override
            public void visitElement(@NotNull PsiElement element) {
              //System.out.println("fast visit "+element + Thread.currentThread());
            }
          };
        }
      };
      Project project = fixture.getProject();
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way
        UIUtil.markAsFocused(fixture.getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
        SeverityRegistrar.getSeverityRegistrar(project); //preload inspection profile

        Disposer.register(fixture.getTestRootDisposable(), () -> fixture.disableInspectionTool(fastTool.getShortName()));
        Disposer.register(fixture.getTestRootDisposable(), () -> fixture.disableInspectionTool(slowTool.getShortName()));
        for (Tools tools : ProjectInspectionProfileManager.getInstance(project).getCurrentProfile().getAllEnabledInspectionTools(project)) {
          fixture.disableInspectionTool(tools.getTool().getShortName());
        }
        fixture.enableInspectionTools(fastTool, slowTool);

        // both inspections should produce their results
        List<HighlightInfo> infos = fixture.getTestDaemonCodeAnalyzer().waitHighlighting(fixture.getFile(), HighlightSeverity.WARNING);
        assertTrue(ContainerUtil.exists(infos, i -> i.getDescription().equals(fieldWarningText.get())), infos.toString());
        assertTrue(ContainerUtil.exists(infos, i -> i.getDescription().equals(fastToolText)), infos.toString());
      });

      fieldWarningText.set("Aha, field, finally!");
      stallSlowToolMs.set(100);
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type("// another comment");
        //System.out.println("-------------");
        fastToolFinished.set(false);
        slowToolFinished.set(false);
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way

        // now when the LIP restarted, we should get back our inspection result very fast, despite very slow processing of every other element
        AtomicBoolean fastToolFinishedFaster = new AtomicBoolean();
        MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(fixture.getEditor().getDocument(), project, true);
        try {
          fixture.getTestDaemonCodeAnalyzer().waitForDaemonToFinish(fixture.getFile(), () -> {
            if (fastToolFinished.get() && !slowToolFinished.get()) {
              fastToolFinishedFaster.set(true);
              boolean fastFound = !DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, 0, fixture.getEditor().getDocument().getTextLength(),
                                                                          info -> !fastToolText.equals(info.getDescription()));
              if (fastFound) {
                fail("Inspection must have removed its own obsolete highlights as soon as it's finished, but got:" +
                     StringUtil.join(model.getAllHighlighters(), Object::toString, "\n   ")+"; thread dump:\n"+ThreadDumper.dumpThreadsToString());
              }
            }
          });
          assertTrue(fastToolFinishedFaster.get(), "Fast inspection must have finished faster than the slow one, but it didn't");
        }
        finally {
          stallSlowToolMs.set(0);
        }
      });
    });
  }

  @Test
  public void testModificationInsideCommentDoesNotAffectNearbyInspectionWarning() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.enableInspectionTool(new ConstantValueInspection());
      @Language("JAVA")
      String text = """
          class AClass {
            public int foo() {
              //<caret>
              if (this == null) return 0;
              return 1;
            }
          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);

      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(DaemonAnalyzerFixture.filter(infos, HighlightSeverity.ERROR)).isEmpty();
      HighlightInfo error = ContainerUtil.find(infos, e->e.getDescription().contains("always 'false'"));
      assertNotNull(error, infos.toString());
      fixture.type("d");
      List<HighlightInfo> infos2 = fixture.waitHighlighting(HighlightSeverity.WARNING);
      HighlightInfo error2 = ContainerUtil.find(infos2, e->e.getDescription().contains("always 'false'"));
      assertNotNull(error2, infos2.toString());
    });
  }

  private static class MyException extends RuntimeException {
    MyException() {
      super("MyPreciousException");
    }
  }

  @Test
  public void testThrowingExceptionFromInspectionMustPropagateUpToTheLogger() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      DefaultLogger.disableStderrDumping(fixture.getTestRootDisposable());
      CountDownLatch inspectionStarted = new CountDownLatch(1);
      MyInspectionBase throwExceptionInspection = new MyInspectionBase() {
        @Override
        public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile psiFile) {
              try {
                inspectionStarted.await();
              }
              catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              throw new MyException();
            }
          };
        }
      };
      fixture.enableInspectionTool(throwExceptionInspection);
      @Language("JAVA")
      String text = """
          class AClass {
          }
        """;
      PsiFile psiFile = fixture.configureByText(JavaFileType.INSTANCE, text);

      Throwable thrown = EdtTestUtil.runInEdtAndGet(() -> {
        try {
          fixture.getTestDaemonCodeAnalyzer().waitForDaemonToFinish(psiFile, () -> inspectionStarted.countDown());
          return null;
        }
        catch (Throwable e) {
          return e;
        }
      });
      assertNotNull(thrown, "'" + Throwable.class.getName() + "' must have been thrown, but the computation completed successfully instead");
      Throwable cause = thrown;
      while (cause instanceof TestLoggerFactory.TestLoggerAssertionError && cause.getCause() != null) {
        cause = cause.getCause();
      }
      assertThat(Objects.requireNonNullElse(cause.getMessage(), "")).contains(new MyException().getMessage());
    });
  }

  @Test
  public void testInspectionMustRemoveItsObsoleteHighlightsImmediatelyAfterVisitingPSIElementTheSecondTimeAndFailingToGenerateTheSameWarningAgain() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            // xxx
            int f;<caret>
        }""";

      String fieldWarningText = "1st run";
      AtomicBoolean fieldIdentifierVisited = new AtomicBoolean();
      AtomicBoolean fieldHighlightsUpdated = new AtomicBoolean();
      AtomicBoolean fieldToolMustWarn = new AtomicBoolean(true);
      // highlight each field on visitField() if said so, stall on every other element
      LocalInspectionTool fieldTool = new MyInspectionBase() {
        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new JavaElementVisitor() {
            @Override
            public void visitIdentifier(@NotNull PsiIdentifier identifier) {
              if (identifier.getParent() instanceof PsiField) {
                if (fieldToolMustWarn.get()) {
                  holder.registerProblem(identifier, fieldWarningText);
                }
                fieldIdentifierVisited.set(true);
              }
            }

            @Override
            public void visitElement(@NotNull PsiElement element) {
              if (fieldIdentifierVisited.get()) {
                // after visitIdentifier() has completed, before the next visit() method is called, the highlights must be updated
                fieldHighlightsUpdated.set(true);
              }
            }
          };
        }
      };

      Project project = fixture.getProject();
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way
        UIUtil.markAsFocused(fixture.getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
        SeverityRegistrar.getSeverityRegistrar(project); //preload inspection profile

        fixture.enableInspectionTools(fieldTool);

        // inspections should produce their results
        List<HighlightInfo> infos = fixture.getTestDaemonCodeAnalyzer().waitHighlighting(fixture.getFile(), HighlightSeverity.WARNING);
        assertTrue(ContainerUtil.exists(infos, i -> i.getDescription().equals(fieldWarningText)), infos.toString());
      });
      assertTrue(fieldIdentifierVisited.get());
      assertTrue(fieldHighlightsUpdated.get());

      fieldIdentifierVisited.set(false);
      fieldHighlightsUpdated.set(false);
      fieldToolMustWarn.set(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type("// another comment\nvoid anotherMethod(){}");
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way

        // now when the LIP restarted, we should observe the range highlighter for the inspection to disappear as soon as visitIdentifier() method is finished
        MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(fixture.getEditor().getDocument(), project, true);
        fixture.getTestDaemonCodeAnalyzer().waitForDaemonToFinish(fixture.getFile(), () -> {
          if (fieldHighlightsUpdated.get()) {
            boolean found = !DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, 0, fixture.getEditor().getDocument().getTextLength(),
                            info -> !fieldWarningText.equals(info.getDescription()));
            if (found) {
              fail("Inspection must have its obsolete highlights removed as soon as its visitIdentifier() is finished, but got:" +
                   StringUtil.join(model.getAllHighlighters(), Object::toString, "\n   ") + "; thread dump:\n" + ThreadDumper.dumpThreadsToString());
            }
          }
        });
      });
      assertTrue(fieldIdentifierVisited.get());
      assertTrue(fieldHighlightsUpdated.get());
    });
  }

  @Test
  public void testRedundantWarningMustNotBlinkOnTypingInsideParameterModifierList() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      DefaultLogger.disableStderrDumping(fixture.getTestRootDisposable());
      fixture.enableInspectionTools(new RedundantSuppressInspection(), new RawUseOfParameterizedTypeInspection());
      @Language("JAVA")
      String text = """
       abstract class Converter<T> {
         abstract T fromString(String value);
       }
       class Base {
        protected Base(String accessor,
                       <caret>Class<? extends Converter> converter) {
          System.out.println(accessor + converter);
        }
       }
       """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertTrue(ContainerUtil.exists(infos, info-> new RawUseOfParameterizedTypeInspection().getShortName()
        .equals(info.getInspectionToolId())), infos.toString());
      fixture.type("@SuppressWarnings(\"rawtypes\")    ") ;
      assertThat(fixture.waitHighlighting(HighlightSeverity.WARNING)).isEmpty();
      fixture.type("\n");
      assertThat(fixture.waitHighlighting(HighlightSeverity.WARNING)).isEmpty();
    });
  }

  @Test
  public void testHighlightsForInvalidPSIMustBeRemovedFastForExampleBeforeTheInspectionsStartRunning() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      DefaultLogger.disableStderrDumping(fixture.getTestRootDisposable());
      LocalInspectionTool myTool = new MyInspectionBase() {
        @Override
        public @NotNull String getShortName() {
          return "my own tool xxx";
        }

        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
          return new JavaElementVisitor() {
            @Override
            public void visitIdentifier(@NotNull PsiIdentifier identifier) {
              if (identifier.getText().contains("xxx")) {
                // by this moment all highlights for invalid PSI must be removed

                List<RangeHighlighter> highlighters = List.of(DocumentMarkupModel.forDocument(fixture.getDocument(fixture.getFile()), fixture.getProject(), true).getAllHighlighters());
                assertFalse(ContainerUtil.exists(highlighters, r -> r.isValid() && HighlightInfo.fromRangeHighlighter(r) != null && getShortName().equals(HighlightInfo.fromRangeHighlighter(r).getInspectionToolId())));

                holder.registerProblem(identifier, "XXX", ProblemHighlightType.WARNING);
              }
            }
          };
        }
      };

      fixture.enableInspectionTools(myTool);

      @Language("JAVA")
      String text = """
       class Base {
         <caret>int xxx;
       }
       """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(ContainerUtil.filter(infos, info-> myTool.getShortName().equals(info.getInspectionToolId()))).hasSize(1);
      fixture.type("// ") ;
      assertThat(fixture.waitHighlighting(HighlightSeverity.WARNING)).isEmpty();
    });
  }
}
