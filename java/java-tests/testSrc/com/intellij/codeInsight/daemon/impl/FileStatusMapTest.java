// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.multiverse.CodeInsightContextUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * tests general daemon behaviour/interruptibility/restart during highlighting
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class FileStatusMapTest extends DaemonAnalyzerTestCase {
  static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  private DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    UndoManager.getInstance(myProject);
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
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
    (EditorTracker.Companion.getInstance(myProject)).setActiveEditors(Arrays.asList(editors));
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    // treat listeners added there as not leaks
    EditorMouseHoverPopupManager.getInstance();
  }

  public void testHighlightersUpdate() throws Exception {
    configureByFile(BASE_PATH + "HighlightersUpdate.java");
    Document document = getDocument(getFile());
    assertNotEmpty(highlightErrors());
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.ERROR, getProject());
    assertSize(1, errors);
    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertNull(dirty);

    type(' ');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertNotNull(dirty);
  }


  public void testNoPsiEventsAltogether() throws Exception {
    configureByFile(BASE_PATH + "HighlightersUpdate.java");
    Document document = getDocument(getFile());
    assertNotEmpty(highlightErrors());
    type(' ');
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertEquals(getFile().getTextRange(), dirty); // have to rehighlight whole file in case no PSI events have come
  }

  public void testRenameClass() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
    
      }
    """);
    Document document = getDocument(getFile());
    assertEmpty(highlightErrors());
    PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    new RenameProcessor(myProject, psiClass, "Class2", false, false).run();
    myDaemonCodeAnalyzer.waitForUpdateFileStatusBackgroundQueueInTests();
    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertEquals(getFile().getTextRange(), dirty);

    assertEmpty(highlightErrors());
    assertTrue(myDaemonCodeAnalyzer.isErrorAnalyzingFinished(getFile()));
  }

  public void testTypingSpace() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
    
      }
    """);
    Document document = getDocument(getFile());
    assertEmpty(highlightErrors());

    type("  ");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement elementAtCaret = myFile.findElementAt(myEditor.getCaretModel().getOffset());
    assertTrue(elementAtCaret instanceof PsiWhiteSpace);
    myDaemonCodeAnalyzer.waitForUpdateFileStatusBackgroundQueueInTests();
    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertEquals(elementAtCaret.getTextRange(), dirty);
    assertEmpty(highlightErrors());
    assertTrue(myDaemonCodeAnalyzer.isErrorAnalyzingFinished(getFile()));
  }

  public void testFileStatusMapDirtyPSICachingWorks() {
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false); // to prevent auto-start highlighting
    UIUtil.dispatchAllInvocationEvents();
    configureByText(JavaFileType.INSTANCE, "class <caret>S { int ffffff =  0;}");
    UIUtil.dispatchAllInvocationEvents();

    int[] creation = {0};
    class Fac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        TextRange textRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), psiFile, Pass.UPDATE_ALL);
        if (textRange == null) return null;
        return new TestFileStatusMapDirtyCachingWorksPass(myProject);
      }

      final class TestFileStatusMapDirtyCachingWorksPass extends TextEditorHighlightingPass {
        private TestFileStatusMapDirtyCachingWorksPass(Project project) {
          super(project, getEditor().getDocument(), false);
          creation[0]++;
        }

        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
        }

        @Override
        public void doApplyInformationToEditor() {
        }
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);
    assertEmpty(highlightErrors());
    assertEquals(1, creation[0]);

    //cached
    assertEmpty(highlightErrors());
    assertEquals(1, creation[0]);
    assertEmpty(highlightErrors());
    assertEquals(1, creation[0]);

    type(' ');
    assertEmpty(highlightErrors());
    assertEquals(2, creation[0]);
    assertEmpty(highlightErrors());
    assertEquals(2, creation[0]);
    assertEmpty(highlightErrors());
    assertEquals(2, creation[0]);
  }

  public void testFileStatusMapDirtyDocumentRangeWorks() {
    configureByText(PlainTextFileType.INSTANCE, "class <caret>S { int ffffff =  0;}");
    UIUtil.dispatchAllInvocationEvents();

    Document document = myEditor.getDocument();
    FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();
    fileStatusMap.disposeDirtyDocumentRangeStorage(document);
    assertNull(fileStatusMap.getCompositeDocumentDirtyRange(document));

    int offset = myEditor.getCaretModel().getOffset();
    type(' ');
    assertEquals(new TextRange(offset, offset+1), fileStatusMap.getCompositeDocumentDirtyRange(document));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(10, 11, "xxx"));
    assertEquals(new TextRange(offset, 13), fileStatusMap.getCompositeDocumentDirtyRange(document));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("  "));
    assertEquals(new TextRange(0, 2), fileStatusMap.getCompositeDocumentDirtyRange(document));
    fileStatusMap.disposeDirtyDocumentRangeStorage(document);
    assertNull(fileStatusMap.getCompositeDocumentDirtyRange(document));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0,"x"));
    assertEquals(new TextRange(0, 1), fileStatusMap.getCompositeDocumentDirtyRange(document));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(1,"x"));
    assertEquals(new TextRange(0, 2), fileStatusMap.getCompositeDocumentDirtyRange(document));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(4,"x"));
    assertEquals(new TextRange(0, 5), fileStatusMap.getCompositeDocumentDirtyRange(document));
  }

  public void testDefensivelyDirtyFlagDoesNotClearPrematurely() {
    class Fac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        return null;
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);

    configureByText(JavaFileType.INSTANCE, "@Deprecated<caret> class S { } ");

    List<HighlightInfo> infos = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertSize(2, infos);

    assertEquals("@Deprecated", infos.get(0).getText());
    assertEquals("S", infos.get(1).getText());

    backspace();
    type('d');

    List<HighlightInfo> after = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);

    assertEquals("@Deprecated", after.get(0).getText());
    assertEquals("S", after.get(1).getText());

    backspace();
    type('d');

    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getTextLength());
    type(" ");

    after = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertSize(2, after);

    assertEquals("@Deprecated", after.get(0).getText());
    assertEquals("S", after.get(1).getText());
  }


  public void testModificationInExcludedFileDoesNotCauseRehighlight() {
    @Language("JAVA")
    String text = "class EEE { void f(){} }";
    VirtualFile excluded = configureByText(JavaFileType.INSTANCE, text).getVirtualFile();
    PsiTestUtil.addExcludedRoot(myModule, excluded.getParent());
    assertTrue(ProjectFileIndex.getInstance(myProject).isExcluded(excluded));

    configureByText(JavaFileType.INSTANCE, "class X { <caret> }");
    assertEmpty(highlightErrors());
    FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileStatusMap();
    TextRange scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
    assertNull(scope);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> ((PsiJavaFile)PsiManager.getInstance(myProject).findFile(excluded)).getClasses()[0].getMethods()[0].delete());

    UIUtil.dispatchAllInvocationEvents();
    scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
    assertNull(scope);
  }

  public void testModificationInWorkspaceXmlDoesNotCauseRehighlight() {
    configureByText(JavaFileType.INSTANCE, "class X { <caret> }");
    StoreUtilKt.runInAllowSaveMode(true, () -> {
      StoreUtil.saveDocumentsAndProjectsAndApp(true);
      VirtualFile workspaceFile = Objects.requireNonNull(getProject().getWorkspaceFile());
      PsiFile excluded = Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(workspaceFile));

      assertEmpty(highlightErrors());
      FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileStatusMap();
      TextRange scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
      assertNull(scope);

      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(getProject()).getDocument(excluded));
        document.insertString(0, "<!-- dsfsd -->");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      UIUtil.dispatchAllInvocationEvents();
      scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
      assertNull(scope);
      return Unit.INSTANCE;
    });
  }

  public void testFileReload() throws Exception {
    VirtualFile file = createFile("a.java", "").getVirtualFile();
    Document document = getDocument(file);
    assertNotNull(document);

    FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
      GCWatcher.tracking(documentManager.getCachedPsiFile(document)).ensureCollected();
      assertNull(documentManager.getCachedPsiFile(document));

      @Language("JAVA")
      String text = "class X { void foo() {}}";
      document.insertString(0, text);
      documentManager.commitAllDocuments();
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));

      FileContentUtilCore.reparseFiles(file);
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));

      findClass("X").getMethods()[0].delete();
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));
    });
  }

  public void testDocumentsMustBeWeaklyReferenced() throws Exception {
    VirtualFile file = createFile("a.java", "blah blah").getVirtualFile();
    AtomicReference<Document> document = new AtomicReference<>(getDocument(file));
    assertNotNull(document.get());
    AtomicReference<PsiFile> psiFile = new AtomicReference<>(PsiDocumentManager.getInstance(myProject).getPsiFile(document.get()));

    FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();
    assertNull(fileStatusMap.getFileDirtyScopeForAllPassesCombined(document.get()));
    fileStatusMap.markWholeFileScopeDirty(document.get(), getTestName(false));
    assertEquals(psiFile.get().getTextRange(), fileStatusMap.getFileDirtyScope(document.get(), psiFile.get(), 0));
    GCWatcher tracking = GCWatcher.tracking(document.get());
    document.set(null);
    psiFile.set(null);
    tracking.ensureCollected(); // WHOLE_RANGE_MARKER does not retain document

    document.set(getDocument(file));
    psiFile.set(PsiDocumentManager.getInstance(myProject).getPsiFile(document.get()));
    for (int pass = 1; pass<=Pass.LAST_PASS; pass++) {
      fileStatusMap.markFileUpToDate(document.get(), CodeInsightContextUtil.getCodeInsightContext(psiFile.get()), pass, new DaemonProgressIndicator());
    }
    for (int pass=1; pass<=Pass.LAST_PASS; pass++) {
      fileStatusMap.assertFileStatusScopeIsNull(document.get(), CodeInsightContextUtil.getCodeInsightContext(psiFile.get()), pass);
    }
    TextRange range = new TextRange(1, 2);
    AppExecutorUtil.getAppExecutorService().submit(() -> ReadAction.run(()->fileStatusMap.markScopeDirty(document.get(), range, getTestName(false)))).get();
    assertEquals(range, fileStatusMap.getFileDirtyScope(document.get(), psiFile.get(), Pass.EXTERNAL_TOOLS));

    tracking = GCWatcher.tracking(document.get());
    document.set(null);
    psiFile.set(null);
    tracking.ensureCollected(); // fileStatusMap RangeMarker does not retain document

    document.set(getDocument(file));
    assertNull(fileStatusMap.getFileDirtyScopeForAllPassesCombined(document.get()));
  }

  public void testChangeDocumentFollowedByImmediateUndoMustDirtyTheInvolvedLinesBecauseRangeHighlightersMightBeDestroyedAndThenNotRestored() {
    PsiFile psiFile = configureByText(JavaFileType.INSTANCE, "blah\nblah<caret>\nblah");
    Document document = psiFile.getFileDocument();

    highlightErrors();
    assertNull(FileStatusMap.getDirtyTextRange(document, psiFile, Pass.LOCAL_INSPECTIONS));

    LightPlatformCodeInsightTestCase.executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE, getEditor(), getProject());
    LightPlatformCodeInsightTestCase.executeAction(IdeActions.ACTION_UNDO, getEditor(), getProject());

    assertEquals(psiFile.getTextRange(), FileStatusMap.getDirtyTextRange(document, psiFile, Pass.LOCAL_INSPECTIONS));
  }

  public void testAfterNoPsiChangeTheWholeFileShouldBeDirty() {
    PsiFile psiFile = configureByText(JavaFileType.INSTANCE, "@Deprecated<caret> class S { } ");
    Document document = psiFile.getFileDocument();
    doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertNull(FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL));

    backspace();
    type('d');

    assertEquals(psiFile.getTextRange(), FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL));
  }
}
