// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TestTimeOut;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * tests {@link HighlightVisitor} behaviour during highlighting
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class DaemonHighlightVisitorRespondToChangesTest extends DaemonAnalyzerTestCase {
  private DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    UndoManager.getInstance(myProject);
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
    PlatformTestUtil.assumeEnoughParallelism();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myEditor != null) {
        Document document = myEditor.getDocument();
        FileDocumentManager.getInstance().reloadFromDisk(document);
      }
      Project project = getProject();
      if (project != null) {
        doPostponedFormatting(project);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myDaemonCodeAnalyzer = null;
      super.tearDown();
    }
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    DaemonProgressIndicator.runInDebugMode(() -> super.runTestRunnable(testRunnable));
  }

  @Override
  protected Sdk getTestProjectJdk() {
    //noinspection removal
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_11;
  }

  @Override
  protected void configureByExistingFile(@NotNull VirtualFile virtualFile) {
    super.configureByExistingFile(virtualFile);
    setActiveEditors(getEditor());
  }

  @Override
  protected VirtualFile configureByFiles(@Nullable File rawProjectRoot, VirtualFile @NotNull ... vFiles) throws IOException {
    VirtualFile file = super.configureByFiles(rawProjectRoot, vFiles);
    setActiveEditors(getEditor());
    return file;
  }

  private void setActiveEditors(Editor @NotNull ... editors) {
    EditorTracker.Companion.getInstance(myProject).setActiveEditors(Arrays.asList(editors));
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    // treat listeners added there as not leaks
    EditorMouseHoverPopupManager.getInstance();
  }

  public void testHighlightInfoGeneratedByHighlightVisitorMustImmediatelyShowItselfOnScreenRightAfterCreation() throws Exception {
    AtomicBoolean xxxMustBeVisible = new AtomicBoolean();
    HighlightVisitor visitor = new MyHighlightCommentsSubstringVisitor(xxxMustBeVisible);
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, getTestRootDisposable());
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
    configureByText(JavaFileType.INSTANCE, text);

    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
    Runnable callbackWhileWaiting = () -> {
      if (xxxMustBeVisible.get()) {
        List<String> myInfos = ContainerUtil.map(filterMy(DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject())), h->h.getDescription());
        assertNotEmpty(myInfos);
      }
    };
    myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, false, callbackWhileWaiting);

    List<HighlightInfo> myWarns = filterMy(doHighlighting());
    assertOneElement(myWarns);
  }

  @NotNull
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
    public boolean suitableForFile(@NotNull PsiFile file) {
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
    public boolean analyze(@NotNull PsiFile file,
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

  public void testHighlightInfoGeneratedByHighlightVisitorMustImmediatelyShowItselfOnScreenRightAfterCreationInBGT() {
    Runnable commentHighlighted = () -> {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      ApplicationManager.getApplication().assertReadAccessAllowed();

      // assert markup is updated as soon as the HighlightInfo is created
      List<HighlightInfo> highlightsFromMarkup = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
      MyHighlightCommentVisitor.assertHighlighted(highlightsFromMarkup);
    };
    HighlightVisitor visitor = new MyHighlightCommentVisitor(commentHighlighted);
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, getTestRootDisposable());
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
    configureByText(JavaFileType.INSTANCE, text);

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    MyHighlightCommentVisitor.assertHighlighted(infos);
  }

  private static class MyHighlightCommentVisitor implements HighlightVisitor {
    private final Runnable commentHighlighted;
    private volatile boolean infoCreated;
    private HighlightInfoHolder myHolder;

    private MyHighlightCommentVisitor(@NotNull Runnable commentHighlighted) {
      this.commentHighlighted = commentHighlighted;
    }

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
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
    public boolean analyze(@NotNull PsiFile file,
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
      assertTrue("HighlightInfo is missing. All available infos are: "+infos, ContainerUtil.exists(infos, info -> info.getDescription().equals("MY2: CMT")));
    }
  }

  public void testDaemonListenerFiresEventsInCorrectOrderEvenWhenHighlightVisitorInterruptsItself() throws Exception {
    List<String> log = Collections.synchronizedList(new ArrayList<>());
    myProject.getMessageBus().connect(getTestRootDisposable())
      .subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
          log.add("F");
        }

        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          log.add("S");
        }

        @Override
        public void daemonCancelEventOccurred(@NotNull String reason) {
          log.add("C");
        }
      });

    @Language("JAVA")
    String text = """
      class X {
        // comment1
        // comment2
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    assertEmpty(highlightErrors());

    log.clear();
    INTERRUPT.set(true);
    COMMENT_HIGHLIGHTED.set(false);
    HighlightVisitor visitor = new MyInterruptingVisitor();
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, getTestRootDisposable());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    myDaemonCodeAnalyzer.restart(getTestName(false));
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    try {
      myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), TextEditorProvider.getInstance().getTextEditor(getEditor()), ArrayUtilRt.EMPTY_INT_ARRAY, true, () -> {});
    }
    catch (ProcessCanceledException ignored) {
    }

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
    MyInterruptingVisitor.assertExistMy(infos);
    assertEquals("[S, C]", log.toString());

    INTERRUPT.set(false);
    COMMENT_HIGHLIGHTED.set(false);
    log.clear();
    try {
      myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), TextEditorProvider.getInstance().getTextEditor(getEditor()), ArrayUtilRt.EMPTY_INT_ARRAY, true, () -> { });
    }
    catch (ProcessCanceledException ignored) {
    }
    finally {
      myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
    }
    infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
    MyInterruptingVisitor.assertExistMy(infos);
    assertEquals("[S, F]", log.toString());
  }

  private static final AtomicBoolean INTERRUPT = new AtomicBoolean();
  private static final AtomicBoolean COMMENT_HIGHLIGHTED = new AtomicBoolean();
  private static class MyInterruptingVisitor implements HighlightVisitor {
    private HighlightInfoHolder myHolder;

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
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
    public boolean analyze(@NotNull PsiFile file,
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
      assertTrue("HighlightInfo is missing. All available infos are: "+infos, ContainerUtil.exists(infos, info -> info.getDescription().equals("MY3: CMT")));
    }
  }

  public void testHighlightVisitorsMustRunIndependentlyAndInParallel() throws Exception {
    @Language("JAVA")
    String text = """
      class X {
        // comment1
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    assertEmpty(highlightErrors());

    MyThinkingHighlightVisitor visitor1 = new MyThinkingHighlightVisitor.MyThinkingHighlightVisitor1(); // must have different classes
    MyThinkingHighlightVisitor visitor2 = new MyThinkingHighlightVisitor.MyThinkingHighlightVisitor2();
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor1, getTestRootDisposable());
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor2, getTestRootDisposable());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    STATE.put("MSG1", new State(new AtomicBoolean(), new AtomicBoolean(true), new AtomicBoolean()));
    STATE.put("MSG2", new State(new AtomicBoolean(), new AtomicBoolean(true), new AtomicBoolean()));
    myDaemonCodeAnalyzer.restart(getTestName(false));
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    TestTimeOut timeOut = TestTimeOut.setTimeout(1, TimeUnit.MINUTES);
    myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), TextEditorProvider.getInstance().getTextEditor(getEditor()), ArrayUtilRt.EMPTY_INT_ARRAY, true, () -> {
      if (timeOut.isTimedOut()) {
        String dump = ThreadDumper.dumpThreadsToString();
        List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
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
        List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
        visitor1.myState().THINK.set(false);
        visitor2.myState().THINK.set(false);
        boolean b = ContainerUtil.exists(infos, info -> visitor1.isMy(info)) && ContainerUtil.exists(infos, info -> visitor2.isMy(info));
        if (!b) {
          System.out.println(infos+"\n---\n"+ThreadDumper.dumpThreadsToString()+"\n====");
        }
        assertTrue(infos.toString(), b);
      }
    });
    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
    assertTrue(infos.toString(), ContainerUtil.exists(infos, info->visitor1.isMy(info)) && ContainerUtil.exists(infos, info->visitor2.isMy(info)));
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
    public boolean suitableForFile(@NotNull PsiFile file) {
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
    public boolean analyze(@NotNull PsiFile file,
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

  public void testTodoRehighlightsItselfEvenOnSmallChanges() {
    @Language("JAVA")
    String text = """
      class X {
        // TODO<caret>
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    assertOneElement(ContainerUtil.filter(doHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO)));

    backspace();
    assertEmpty(ContainerUtil.filter(doHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO)));

    type('O');
    assertOneElement(ContainerUtil.filter(doHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO)));
  }

  public void testTodoDoesNotClearTodosUnaffectedBySmallChange() {
    @Language("JAVA")
    String text = """
      class X {
        void foo() {
          <caret>
        }
        // todo blah
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    assertOneElement(ContainerUtil.filter(doHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO)));

    for (int i=0; i<10; i++) {
      type("X");
      assertOneElement(ContainerUtil.filter(doHighlighting(HighlightSeverity.INFORMATION), h -> h.type.equals(HighlightInfoType.TODO)));
    }
  }

  public void testHighlightingVisitorDisabledAtSomePointEgSemanticHighlightingBeingDisabledMustRemoveAllHighlightersOfOutdatedVisitors() {
    @Language("JAVA")
    String text = """
      class X {
        void foo(int wwwwwwwwwwwwwwwww) {
        }
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);
    assertEmpty(ContainerUtil.filter(doHighlighting(), info -> info.type == RainbowHighlighter.RAINBOW_ELEMENT));
    CodeInsightTestFixtureImpl.runWithRainbowEnabled(true, () -> {
      myDaemonCodeAnalyzer.restart(getTestName(false));
      assertNotEmpty(ContainerUtil.filter(doHighlighting(), info -> info.type == RainbowHighlighter.RAINBOW_ELEMENT));
    });
    myDaemonCodeAnalyzer.restart(getTestName(false));
    assertEmpty(ContainerUtil.filter(doHighlighting(), info -> info.type == RainbowHighlighter.RAINBOW_ELEMENT));
  }
}
