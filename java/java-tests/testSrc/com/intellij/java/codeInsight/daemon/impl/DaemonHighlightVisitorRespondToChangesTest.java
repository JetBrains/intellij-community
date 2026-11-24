// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.stubs.StubIdList;
import com.intellij.psi.stubs.StubProcessingHelperBase;
import com.intellij.psi.stubs.StubRecoveryExtraStep;
import com.intellij.psi.stubs.StubRecoveryService;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.TestTimeOut;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesFixture.daemonRespondToChangesFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * tests {@link HighlightVisitor} behaviour during highlighting
 */
@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
@TestApplication
public class DaemonHighlightVisitorRespondToChangesTest {
  private static final Logger LOG = Logger.getInstance(DaemonHighlightVisitorRespondToChangesTest.class);

  private final TestFixture<DaemonRespondToChangesFixture> daemonFixture = daemonRespondToChangesFixture();

  @BeforeEach
  void setUp() {
    UndoManager.getInstance(daemonFixture.get().getProject());
    PlatformTestUtil.assumeEnoughParallelism();
  }

  @Test
  public void testHighlightInfoGeneratedByHighlightVisitorMustImmediatelyShowItselfOnScreenRightAfterCreation() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      AtomicBoolean xxxMustBeVisible = new AtomicBoolean();
      HighlightVisitor visitor = new MyHighlightCommentsSubstringVisitor(xxxMustBeVisible);
      fixture.getProject().getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, fixture.getTestRootDisposable());
      @Language("JAVA")
      String text = """
        class X {
          void f(boolean b) {
            if (b) {
              // xxx
            }
          }
        }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Document document = fixture.getEditor().getDocument();
      PsiFile file = fixture.getFile();

      Runnable callbackWhileWaiting = () -> {
        if (xxxMustBeVisible.get()) {
          List<String> myInfos = ContainerUtil.map(filterMy(DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, fixture.getProject())), h->h.getDescription());
          assertThat(myInfos).isNotEmpty();
        }
      };
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> testDaemon.waitForDaemonToFinish(file, callbackWhileWaiting));

      List<HighlightInfo> myWarns = filterMy(fixture.waitHighlighting(HighlightSeverity.INFORMATION));
      assertThat(myWarns).hasSize(1);
    });
  }

  @Test
  public void testHighlightingRunsAgainAfterStubIndexMismatch() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> doTestHighlightingRunsAgainAfterStubIndexMismatch(fixture, true));
  }

  @Test
  public void testHighlightingRunsAgainAfterDependencyStubIndexMismatch() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> doTestHighlightingRunsAgainAfterStubIndexMismatch(fixture, false));
  }

  private static void doTestHighlightingRunsAgainAfterStubIndexMismatch(@NotNull DaemonRespondToChangesFixture fixture, boolean openInconsistentFile) {
    Project project = fixture.getProject();
    var dependency = fixture.createFile("Dependency.java", "class Dependency {}").getVirtualFile();
    var scheduled = new AtomicBoolean();
    var cachesDropped = new AtomicBoolean();
    var attempts = new AtomicInteger();
    var completed = new AtomicBoolean();
    var recovery = StubRecoveryService.getInstance();
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), StubRecoveryService.class,
      (p, file, extraSteps) -> {
        if (file.equals(dependency)) {
          assertSame(project, p);
          assertThat(List.of(extraSteps)).contains(StubRecoveryExtraStep.DROP_PSI_CACHES);
          scheduled.set(true);
        }
        recovery.scheduleReindexing(p, file, extraSteps);
      }, fixture.getTestRootDisposable());
    project.getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC,
      new AnyPsiChangeListener() {
        @Override
        public void afterPsiChanged(boolean isPhysical) {
          if (scheduled.get()) cachesDropped.set(true);
        }
      });
    var helper = new StubProcessingHelperBase() {
      @Override
      protected void onInternalError(VirtualFile file) { }
    };
    var fileName = openInconsistentFile ? "Dependency.java" : "Use.java";
    HighlightVisitor visitor = new HighlightVisitor() {
      @Override
      public boolean suitableForFile(@NotNull PsiFile file) {
        return file.getName().equals(fileName);
      }

      @Override
      public void visit(@NotNull PsiElement element) { }

      @Override
      public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile,
                             @NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
        if (attempts.incrementAndGet() == 1) {
          helper.processStubsInFile(project, dependency, new StubIdList(0),
                                   _ -> { fail("The processor must reject the file PSI"); return false; },
                                   null, PsiClass.class, () -> "test stub inconsistency during highlighting");
          assertTrue(scheduled.get(), "The inconsistency must schedule reindexing");
        }
        action.run();
        if (cachesDropped.get()) {
          holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(0, 1)
                       .description("Highlighting completed after stub recovery").create());
          completed.set(true);
        }
        return true;
      }

      @Override
      @SuppressWarnings("CloneDoesntCallSuperClone")
      public @NotNull HighlightVisitor clone() {
        return this;
      }
    };
    project.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR)
      .registerExtension(visitor, fixture.getTestRootDisposable());
    fixture.configureByExistingFile(openInconsistentFile ? dependency :
                                    fixture.createFile("Use.java", "class Use { Dependency value; }").getVirtualFile());
    PsiFile psiFile = fixture.getFile();
    DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.waitWithEventsDispatching(
      "Highlighting must resume after stub recovery", () -> completed.get() && daemon.isAllAnalysisFinished(psiFile), 60));
    assertTrue(scheduled.get(), "The inconsistency must schedule reindexing");
    assertTrue(cachesDropped.get(), "The recovery must drop the PSI caches");
    assertTrue(attempts.get() >= 2, "Highlighting must run again without an edit or an explicit restart");
    Document document = fixture.getEditor().getDocument();
    assertTrue(ContainerUtil.exists(
      getHighlights(document, project),
      info -> "Highlighting completed after stub recovery".equals(info.getDescription())));
  }

  private static @NotNull List<HighlightInfo> getHighlights(@NotNull Document document, @NotNull Project project) {
    return ReadAction.computeBlocking(() -> DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project));
  }

  @NotNull
  @Unmodifiable
  private static List<HighlightInfo> filterMy(@NotNull List<? extends HighlightInfo> infos) {
    return ContainerUtil.filter(infos, h -> MyHighlightCommentsSubstringVisitor.isMy(h));
  }

  private static class MyHighlightCommentsSubstringVisitor implements HighlightVisitor {
    private final AtomicBoolean xxxMustBeVisible;
    private volatile boolean infoCreated;
    private HighlightInfoHolder myHolder;

    MyHighlightCommentsSubstringVisitor(@NotNull AtomicBoolean xxxMustBeVisible) {
      this.xxxMustBeVisible = xxxMustBeVisible;
    }

    @Override
    public boolean suitableForFile(@NotNull PsiFile psiFile) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      if (element instanceof PsiComment) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange().cutOut(TextRange.create(0, 1))).description("MY: XXX").create());
        infoCreated = true;
      }
      else if (infoCreated) {
        xxxMustBeVisible.set(true);
      }
    }

    static boolean isMy(HighlightInfo info) {
      return HighlightSeverity.WARNING.equals(info.getSeverity()) && "MY: XXX".equals(info.getDescription());
    }

    @Override
    public boolean analyze(@NotNull PsiFile psiFile,
                           boolean updateWholeFile,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull Runnable action) {
      myHolder = holder;
      action.run();
      return true;
    }

    @Override
    public @NotNull HighlightVisitor clone() {
      return new MyHighlightCommentsSubstringVisitor(xxxMustBeVisible);
    }
  }

  @Test
  public void testHighlightInfoGeneratedByHighlightVisitorMustImmediatelyShowItselfOnScreenRightAfterCreationInBGT() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Runnable commentHighlighted = () -> {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        ApplicationManager.getApplication().assertReadAccessAllowed();

        // assert markup is updated as soon as the HighlightInfo is created
        List<HighlightInfo> highlightsFromMarkup = DaemonCodeAnalyzerImpl.getHighlights(fixture.getEditor().getDocument(), HighlightSeverity.WARNING, fixture.getProject());
        MyHighlightCommentVisitor.assertHighlighted(highlightsFromMarkup);
      };
      HighlightVisitor visitor = new MyHighlightCommentVisitor(commentHighlighted);
      fixture.getProject().getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, fixture.getTestRootDisposable());
      @Language("JAVA")
      String text = """
        class X {
          void f(boolean b) {
            if (b) {
              // xxx
            }
          }
        }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);

      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      MyHighlightCommentVisitor.assertHighlighted(infos);
    });
  }

  private static class MyHighlightCommentVisitor implements HighlightVisitor {
    private final Runnable commentHighlighted;
    private volatile boolean infoCreated;
    private HighlightInfoHolder myHolder;

    private MyHighlightCommentVisitor(@NotNull Runnable commentHighlighted) {
      this.commentHighlighted = commentHighlighted;
    }

    @Override
    public boolean suitableForFile(@NotNull PsiFile psiFile) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      if (element instanceof PsiComment) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange()).description("MY2: CMT").create());
        infoCreated = true;
      }
      else if (infoCreated) {
        commentHighlighted.run();
      }
    }

    @Override
    public boolean analyze(@NotNull PsiFile psiFile,
                           boolean updateWholeFile,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull Runnable action) {
      myHolder = holder;
      action.run();
      return true;
    }

    @Override
    public @NotNull HighlightVisitor clone() {
      return new MyHighlightCommentVisitor(commentHighlighted);
    }

    private static void assertHighlighted(List<? extends HighlightInfo> infos) {
      assertTrue(ContainerUtil.exists(infos, info -> info.getDescription().equals("MY2: CMT")), "HighlightInfo is missing. All available infos are: "+infos);
    }
  }

  @Test
  public void testDaemonListenerFiresEventsInCorrectOrderEvenWhenHighlightVisitorInterruptsItself() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      List<String> log = Collections.synchronizedList(new ArrayList<>());
      project.getMessageBus().connect(fixture.getTestRootDisposable())
        .subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
          @Override
          public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
            LOG.debug("DaemonListener.daemonFinished");
            log.add("F");
          }

          @Override
          public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
            LOG.debug("DaemonListener.daemonStarting");
            log.add("S");
          }

          @Override
          public void daemonCancelEventOccurred(@NotNull String reason) {
            LOG.debug("DaemonListener.daemonCancelEventOccurred");
            log.add("C");
          }
        });

      @Language("JAVA")
      String text = """
        class X {
          // comment1
          // comment2
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      INTERRUPT.set(true);
      COMMENT_HIGHLIGHTED.set(false);
      project.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(new MyInterruptingVisitor(), fixture.getTestRootDisposable());
      LOG.debug("================================================");
      PsiFile file = fixture.getFile();
      Document document = fixture.getEditor().getDocument();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      String testName = fixture.getTestName(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        UIUtil.dispatchAllInvocationEvents();
        daemon.restart(testName);
        log.clear();
        try {
          testDaemon.waitForDaemonToFinish(file);
        }
        catch (ProcessCanceledException ignored) {
        }
        // to avoid canceled but still inflight passes to cancel the subsequent highlighting (via com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.cancelAndRestartDaemonLater)
        testDaemon.waitForTermination();
        UIUtil.dispatchAllInvocationEvents();
        List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project);
        MyInterruptingVisitor.assertExistMy(infos);
        assertTrue(log.toString().startsWith("[S, C"), log.toString());
      });
      LOG.trace("---------------------2");
      INTERRUPT.set(false);
      COMMENT_HIGHLIGHTED.set(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        daemon.restart(testName);
        log.clear();
        try {
          testDaemon.waitForDaemonToFinish(file);
        }
        catch (ProcessCanceledException ignored) {
        }
        List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project);
        MyInterruptingVisitor.assertExistMy(infos);
        assertEquals("[S, F]", log.toString());
      });
    });
  }

  private static final AtomicBoolean INTERRUPT = new AtomicBoolean();
  private static final AtomicBoolean COMMENT_HIGHLIGHTED = new AtomicBoolean();
  private static class MyInterruptingVisitor implements HighlightVisitor {
    private HighlightInfoHolder myHolder;

    @Override
    public boolean suitableForFile(@NotNull PsiFile psiFile) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      if (element instanceof PsiComment) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange()).description("MY3: CMT").create());
        COMMENT_HIGHLIGHTED.set(true);
      }
      else if (COMMENT_HIGHLIGHTED.get() && INTERRUPT.get()) { // throw interrupt after the comment was highlighted because visit() should complete
        throw new ProcessCanceledException();
      }
    }

    @Override
    public boolean analyze(@NotNull PsiFile psiFile,
                           boolean updateWholeFile,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull Runnable action) {
      myHolder = holder;
      action.run();
      return true;
    }

    @Override
    public @NotNull HighlightVisitor clone() {
      return new MyInterruptingVisitor();
    }

    private static void assertExistMy(List<? extends HighlightInfo> infos) {
      assertTrue(ContainerUtil.exists(infos, info -> info.getDescription().equals("MY3: CMT")), "HighlightInfo is missing. All available infos are: "+infos);
    }
  }

  @Test
  public void testHighlightVisitorsMustRunIndependentlyAndInParallel() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      @Language("JAVA")
      String text = """
        class X {
          // comment1
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      MyThinkingHighlightVisitor visitor1 = new MyThinkingHighlightVisitor.MyThinkingHighlightVisitor1(); // must have different classes
      MyThinkingHighlightVisitor visitor2 = new MyThinkingHighlightVisitor.MyThinkingHighlightVisitor2();
      project.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor1, fixture.getTestRootDisposable());
      project.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor2, fixture.getTestRootDisposable());

      EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);

      STATE.put("MSG1", new State(new AtomicBoolean(), new AtomicBoolean(true), new AtomicBoolean()));
      STATE.put("MSG2", new State(new AtomicBoolean(), new AtomicBoolean(true), new AtomicBoolean()));
      PsiFile file = fixture.getFile();
      Document document = fixture.getEditor().getDocument();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      String testName = fixture.getTestName(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        daemon.restart(testName);
        TestTimeOut timeOut = TestTimeOut.setTimeout(1, TimeUnit.MINUTES);
        testDaemon.waitForDaemonToFinish(file, () -> {
          if (timeOut.isTimedOut()) {
            String dump = ThreadDumper.dumpThreadsToString();
            List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project);
            String message = "Timed out\ninfos=" + infos + "\n"
                             + "; ForkJoinPool.commonPool().getParallelism()=" + ForkJoinPool.commonPool().getParallelism()
                             + ";visitor1.myState().THINKING.get()=" + visitor1.myState().THINKING.get()
                             + ";visitor2.myState().THINKING.get()=" + visitor2.myState().THINKING.get()
                             + ";visitor1.myState().THINK.get()=" + visitor1.myState().THINK.get()
                             + ";visitor2.myState().THINK.get()=" + visitor2.myState().THINK.get()
                             + ";visitor1.myState().COMMENT_HIGHLIGHTED.get()=" + visitor1.myState().COMMENT_HIGHLIGHTED.get()
                             + ";visitor2.myState().COMMENT_HIGHLIGHTED.get()=" + visitor2.myState().COMMENT_HIGHLIGHTED.get()
                             + "\n" + dump;
            visitor1.myState().THINK.set(false);
            visitor2.myState().THINK.set(false);
            visitor1.myState().THINKING.set(false);
            visitor2.myState().THINKING.set(false);
            fail(message);
          }
          if (visitor1.myState().THINKING.get() && visitor2.myState().THINKING.get()) {
            // if two visitors are paused, it means they both have visited comments. check that corresponding highlights are in the markup model
            List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project);
            visitor1.myState().THINK.set(false);
            visitor2.myState().THINK.set(false);
            boolean b = ContainerUtil.exists(infos, info -> visitor1.isMy(info)) && ContainerUtil.exists(infos, info -> visitor2.isMy(info));
            if (!b) {
              System.out.println(infos+"\n---\n"+ThreadDumper.dumpThreadsToString()+"\n====");
            }
            assertTrue(b, infos.toString());
          }
        });
      });
      List<HighlightInfo> infos = getHighlights(document, project);
      assertTrue(ContainerUtil.exists(infos, info->visitor1.isMy(info)) && ContainerUtil.exists(infos, info->visitor2.isMy(info)), infos.toString());
    });
  }

  private record State(@NotNull AtomicBoolean COMMENT_HIGHLIGHTED, @NotNull AtomicBoolean THINK, @NotNull AtomicBoolean THINKING) {}
  private static final Map<String, State> STATE = new ConcurrentHashMap<>(); // must keep the state out of visitors because of their peculiar lifecycle
  // highlight comment and pause for a bit (to check the other highlight visitors are run in parallel)
  private static abstract class MyThinkingHighlightVisitor implements HighlightVisitor {
    private HighlightInfoHolder myHolder;
    private final String MSG;

    private MyThinkingHighlightVisitor(String MSG) {
      this.MSG = MSG;
    }

    @Override
    public abstract @NotNull HighlightVisitor clone();

    @Override
    public boolean suitableForFile(@NotNull PsiFile psiFile) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      LOG.debug("about to visit " + element + "; this=" + this+"; myState="+myState()+"; "+Thread.currentThread());
      if (element instanceof PsiComment) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange()).description(MSG).create();
        myHolder.add(info);
        myState().COMMENT_HIGHLIGHTED.set(true);
        LOG.debug("highlighted " + element + "; "+info+"; this=" + this+"; myState="+myState()+"; "+Thread.currentThread());
      }
      else if (myState().COMMENT_HIGHLIGHTED.get()) {
        LOG.debug("start thinking about " + element + "; this=" + this+"; myState="+myState()+"; "+Thread.currentThread());
        myState().THINKING.set(true);
        while (myState().THINK.get()) {
          fjpAwareSleep(1);
        }
        LOG.debug("stopped thinking about " + element + "; this=" + this+"; myState="+myState()+"; "+Thread.currentThread());
      }
      LOG.debug("end of visit " + element + "; this=" + this+"; myState="+myState()+"; "+Thread.currentThread());
    }

    @Override
    public boolean analyze(@NotNull PsiFile psiFile,
                           boolean updateWholeFile,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull Runnable action) {
      myHolder = holder;
      action.run();
      return true;
    }

    boolean isMy(@NotNull HighlightInfo info) {
      return HighlightSeverity.WARNING.equals(info.getSeverity()) && MSG.equals(info.getDescription());
    }

    State myState() {
      return STATE.get(MSG);
    }

    private static class MyThinkingHighlightVisitor1 extends MyThinkingHighlightVisitor {
      private MyThinkingHighlightVisitor1() {
        super("MSG1");
      }

      @Override
      public @NotNull HighlightVisitor clone() {
        return new MyThinkingHighlightVisitor1();
      }
      @Override
      public void visit(@NotNull PsiElement element) {
        super.visit(element); // for stacktrace
      }
    }
    private static class MyThinkingHighlightVisitor2 extends MyThinkingHighlightVisitor {
      private MyThinkingHighlightVisitor2() {
        super("MSG2");
      }

      @Override
      public @NotNull HighlightVisitor clone() {
        return new MyThinkingHighlightVisitor2();
      }

      @Override
      public void visit(@NotNull PsiElement element) {
        super.visit(element); // for stacktrace
      }
    }
  }

  private static void fjpAwareSleep(int millis) {
    try {
      ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
        @Override
        public boolean block() {
          TimeoutUtil.sleep(millis);
          return true;
        }

        @Override
        public boolean isReleasable() {
          return false;
        }
      });
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testTodoRehighlightsItselfEvenOnSmallChanges() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          // TODO<caret>
        }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(ContainerUtil.filter(
        fixture.waitHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO))).hasSize(1);

      fixture.backspace();
      assertThat(ContainerUtil.filter(
        fixture.waitHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO))).isEmpty();

      fixture.type('O');
      assertThat(ContainerUtil.filter(
        fixture.waitHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO))).hasSize(1);
    });
  }

  @Test
  public void testTodoDoesNotClearTodosUnaffectedBySmallChange() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          void foo() {
            <caret>
          }
          // todo blah
        }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(ContainerUtil.filter(
        fixture.waitHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO))).hasSize(1);

      for (int i=0; i<10; i++) {
        fixture.type("X");
        assertThat(ContainerUtil.filter(
          fixture.waitHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO))).hasSize(1);
      }
    });
  }

  @Test
  public void testHighlightingVisitorDisabledAtSomePointEgSemanticHighlightingBeingDisabledMustRemoveAllHighlightersOfOutdatedVisitors() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          void foo(int wwwwwwwwwwwwwwwww) {
          }
        }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(ContainerUtil.filter(
        fixture.waitHighlighting(HighlightSeverity.INFORMATION), info -> info.type == RainbowHighlighter.RAINBOW_ELEMENT)).isEmpty();
      CodeInsightTestFixtureImpl.runWithRainbowEnabled(true, () -> {
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        assertThat(ContainerUtil.filter(
          fixture.waitHighlighting(HighlightSeverity.INFORMATION), info -> info.type == RainbowHighlighter.RAINBOW_ELEMENT)).isNotEmpty();
      });
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      assertThat(ContainerUtil.filter(
        fixture.waitHighlighting(HighlightSeverity.INFORMATION), info -> info.type == RainbowHighlighter.RAINBOW_ELEMENT)).isEmpty();
    });
  }


  private static class MyCheckingCallsVisitor implements HighlightVisitor {
    private static volatile boolean ANALYZE_CALLED;
    private static volatile boolean VISIT_CALLED;

    @Override
    public boolean suitableForFile(@NotNull PsiFile psiFile) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      VISIT_CALLED = true;
    }

    @Override
    public boolean analyze(@NotNull PsiFile psiFile,
                           boolean updateWholeFile,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull Runnable action) {
      ANALYZE_CALLED = true;
      action.run();
      return true;
    }

    @Override
    public @NotNull HighlightVisitor clone() {
      return new MyCheckingCallsVisitor();
    }
  }
  @Test
  public void testHighlightingVisitorAnalyzeMethodMustNotBeCalledIfTheHighlightingIsDisabledForThisFile() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          void foo(int wwwwwwwwwwwwwwwww) {
          }
        }
        """;
      HighlightVisitor visitor = new MyCheckingCallsVisitor();
      fixture.getProject().getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, fixture.getTestRootDisposable());
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        MyCheckingCallsVisitor.VISIT_CALLED = false;
        MyCheckingCallsVisitor.ANALYZE_CALLED = false;
        fixture.configureByText(JavaFileType.INSTANCE, text);
        testDaemon.waitHighlighting(fixture.getFile(), HighlightSeverity.INFORMATION);
      });
      assertTrue(MyCheckingCallsVisitor.ANALYZE_CALLED);
      assertTrue(MyCheckingCallsVisitor.VISIT_CALLED);

      HighlightingSettingsPerFile.getInstance(fixture.getProject()).setHighlightingSettingForRoot(fixture.getFile(), FileHighlightingSetting.SKIP_HIGHLIGHTING);
      MyCheckingCallsVisitor.VISIT_CALLED = false;
      MyCheckingCallsVisitor.ANALYZE_CALLED = false;
      fixture.getDaemonCodeAnalyzer().restart(this);
      fixture.waitHighlighting(HighlightSeverity.INFORMATION);
      assertFalse(MyCheckingCallsVisitor.VISIT_CALLED);
      assertFalse(MyCheckingCallsVisitor.ANALYZE_CALLED);
    });
  }
}
