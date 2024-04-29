// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.IntentionContainer;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspectionBase;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * test light bulb behaviour, intention action update and application
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class LightBulbTest extends DaemonAnalyzerTestCase {
  static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  private DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    UndoManager.getInstance(myProject);
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
    DaemonProgressIndicator.setDebug(true);
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
  protected Sdk getTestProjectJdk() {
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
    ((EditorTrackerImpl)EditorTracker.getInstance(myProject)).setActiveEditors(Arrays.asList(editors));
  }

  @Override
  protected boolean doTestLineMarkers() {
    return true;
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new FieldCanBeLocalInspection(),
      new RequiredAttributesInspectionBase(),
      new CheckDtdReferencesInspection(),
      new AccessStaticViaInstance(),
    };
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    // treat listeners added there as not leaks
    EditorMouseHoverPopupManager.getInstance();
  }


  public void testBulbAppearsAfterType() {
    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    Set<LightweightHint> shown = new ReferenceOpenHashSet<>();
    getProject().getMessageBus().connect().subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      @Override
      public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
        shown.add(hint);
        hint.addHintListener(event -> shown.remove(hint));
      }
    });

    assertNotEmpty(highlightErrors());

    IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));

    type("x");
    assertNotEmpty(highlightErrors());
    hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));
  }

  public void testBulbMustDisappearAfterPressEscape() {
    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    Set<LightweightHint> shown = new ReferenceOpenHashSet<>();
    getProject().getMessageBus().connect().subscribe(EditorHintListener.TOPIC,
                                                     new EditorHintListener() {
                                                       @Override
                                                       public void hintShown(@NotNull Editor editor,
                                                                             @NotNull LightweightHint hint,
                                                                             int flags,
                                                                             @NotNull HintHint hintInfo) {
                                                         shown.add(hint);
                                                         hint.addHintListener(event -> shown.remove(hint));
                                                       }
                                                     });

    assertNotEmpty(highlightErrors());

    IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));
    assertTrue(hintComponent.hasVisibleLightBulbOrPopup());

    CommandProcessor.getInstance().executeCommand(getProject(), () -> EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_EDITOR_ESCAPE, true), "", null, getEditor().getDocument());

    assertNotEmpty(highlightErrors());
    hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNull(hintComponent);

    // the bulb must reappear when the caret moved
    caretLeft();
    assertNotEmpty(highlightErrors());
    IntentionHintComponent hintComponentAfter = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponentAfter);
    assertFalse(hintComponentAfter.isDisposed());
    assertNotNull(hintComponentAfter.getComponentHint());
    assertTrue(shown.contains(hintComponentAfter.getComponentHint()));
    assertTrue(hintComponentAfter.hasVisibleLightBulbOrPopup());
  }

  public void testLightBulbDoesNotUpdateIntentionsInEDT() {
    IntentionAction longLongUpdate = new AbstractIntentionAction() {
      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
      }

      @Nls
      @NotNull
      @Override
      public String getText() {
        return "LongAction";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        return true;
      }
    };
    IntentionManager.getInstance().addAction(longLongUpdate);
    Disposer.register(getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(longLongUpdate));
    configureByText(JavaFileType.INSTANCE, "class X { <caret>  }");
    DaemonRespondToChangesTest.makeEditorWindowVisible(new Point(0, 0), myEditor);
    doHighlighting();
    myDaemonCodeAnalyzer.restart();
    DaemonRespondToChangesTest.runWithReparseDelay(0, () -> {
      for (int i = 0; i < 1000; i++) {
        caretRight();
        UIUtil.dispatchAllInvocationEvents();
        caretLeft();
        Object updateProgress = new HashMap<>(myDaemonCodeAnalyzer.getUpdateProgress());
        long waitForDaemonStart = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.getUpdateProgress().equals(updateProgress) && System.currentTimeMillis() < waitForDaemonStart + 5000) { // wait until the daemon started
          UIUtil.dispatchAllInvocationEvents();
        }
        if (myDaemonCodeAnalyzer.getUpdateProgress().equals(updateProgress)) {
          throw new RuntimeException("Daemon failed to start in 5000 ms");
        }
        long start = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.isRunning() && System.currentTimeMillis() < start + 500) {
          UIUtil.dispatchAllInvocationEvents(); // wait for a bit more until ShowIntentionsPass.doApplyInformationToEditor() called
        }
      }
    });
  }

  public void testLightBulbIsHiddenWhenFixRangeIsCollapsed() {
    configureByText(JavaFileType.INSTANCE, "class S { void foo() { boolean <selection>var; if (va<caret>r</selection>) {}} }");
    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    Set<LightweightHint> visibleHints = new ReferenceOpenHashSet<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      @Override
      public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
        visibleHints.add(hint);
        hint.addHintListener(new HintListener() {
          @Override
          public void hintHidden(@NotNull EventObject event) {
            visibleHints.remove(hint);
            hint.removeHintListener(this);
          }
        });
      }
    });

    assertNotEmpty(highlightErrors());
    UIUtil.dispatchAllInvocationEvents();
    IntentionHintComponent lastHintBeforeDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(lastHintBeforeDeletion);
    IntentionContainer lastHintIntentions = lastHintBeforeDeletion.getCachedIntentions();
    assertNotNull(lastHintIntentions);
    assertTrue(lastHintIntentions.toString(),
               ContainerUtil.exists(lastHintIntentions.getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")));

    delete(myEditor);
    assertNotEmpty(highlightErrors());
    UIUtil.dispatchAllInvocationEvents();
    IntentionHintComponent lastHintAfterDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    // it must be either hidden or not have that error anymore
    if (lastHintAfterDeletion == null) {
      assertEmpty(visibleHints);
    }
    else {
      IntentionContainer after = lastHintAfterDeletion.getCachedIntentions();
      assertNotNull(after);
      assertFalse(after.toString(), ContainerUtil.exists(after.getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")));
    }
  }

  public void testIntentionActionIsAvailableMustBeQueriedOnlyOncePerHighlightingSession() {
    Map<ProgressIndicator, Throwable> isAvailableCalled = new ConcurrentHashMap<>();
    IntentionAction action = new AbstractIntentionAction() {
      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getText() {
        return "My";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        DaemonProgressIndicator indicator = (DaemonProgressIndicator)ProgressIndicatorProvider.getGlobalProgressIndicator();
        Throwable alreadyCalled = isAvailableCalled.put(indicator, new Throwable());
        if (alreadyCalled != null) {
          throw new IllegalStateException(" .isAvailable() already called in:\n---------------\n" +
                                          ExceptionUtil.getThrowableText(alreadyCalled) + "\n-----------");
        }
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      }
    };
    IntentionManager.getInstance().addAction(action);
    Disposer.register(getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(action));

    @Language("JAVA")
    String text = "class X { }";
    configureByText(JavaFileType.INSTANCE, text);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().setText(text));
    doHighlighting();
    myDaemonCodeAnalyzer.restart();
    doHighlighting();
  }
}
