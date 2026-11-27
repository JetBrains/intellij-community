// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lang.documentation;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.ide.DataManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaDocumentationTarget;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.platform.ide.documentation.ActionsKt.DOCUMENTATION_TARGETS;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public final class DocumentationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testDocumentationTargets() {
    DataContext editorContext = configureJavaEditorAndGetItsContext(false, "class <caret>A {}");

    getAndCheckTargets(editorContext);
  }

  @Test
  public void testInjectedCachingIsNotHarmfulForObtainingTopLevelEditor() {
    DataContext editorContext = configureJavaEditorAndGetItsContext(true, "class <caret>A {}");

    EdtTestUtil.runInEdtAndWait(() -> {
      Editor editor = CommonDataKeys.EDITOR.getData(editorContext);
      assertInstanceOf(EditorImpl.class, editor);
      Editor editor2 = CommonDataKeys.EDITOR.getData(editorContext);
      assertSame(editor, editor2);
    });

    getAndCheckTargets(editorContext);
  }

  @Test
  public void testDocumentationTargetsInInjected() {
    DataContext editorContext = configureJavaEditorAndGetItsContext(true, """
      public class A {
          public static void main(String... args) {
              //language=JAVA
              String x = "class B {static {System.ex<caret>it();}}";
          }
      }""");

    List<DocumentationTarget> targets = getAndCheckTargets(editorContext);

    JavaDocumentationTarget singleTarget = (JavaDocumentationTarget)targets.getFirst();
    assertInstanceOf(ClsMethodImpl.class, singleTarget.getElement());
  }

  private static @NotNull List<DocumentationTarget> getAndCheckTargets(DataContext dataContext) {
    List<DocumentationTarget> targets = ReadAction.computeBlocking(() -> dataContext.getData(DOCUMENTATION_TARGETS));
    assertThat(targets).hasSize(1);
    assertInstanceOf(JavaDocumentationTarget.class, targets.getFirst());
    return targets;
  }

  private DataContext configureJavaEditorAndGetItsContext(boolean makeContextInjected, String fileText) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    // LightPlatformCodeInsightTestCase.setupEditorForInjectedLanguage did nothing in this test
    myFixture.setCaresAboutInjection(false);
    daemonFixture.get().configureFromFileText("A.java", fileText);
    return EdtTestUtil.runInEdtAndGet(() -> {
      EditorEx editor = createPsiAwareEditorFromCurrentFile(myFixture);
      DataContext editorContext = editor.getDataContext();
      if (makeContextInjected) {
        editorContext = AnActionEvent.getInjectedDataContext(editorContext);
      }
      return editorContext;
    });
  }

  // LightPlatformCodeInsightTestCase.createPsiAwareEditorFromCurrentFile
  private static EditorEx createPsiAwareEditorFromCurrentFile(LightJavaCodeInsightTestFixture myFixture) {
    Project project = myFixture.getProject();
    int injectionOffset = myFixture.getEditor().getCaretModel().getOffset();
    // This call is to obtain injected context in the created editor
    InjectedLanguageManager.getInstance(project).findInjectedElementAt(myFixture.getFile(), injectionOffset);
    VirtualFile vFile = myFixture.getFile().getVirtualFile();
    PsiAwareTextEditorProvider editorProvider = new PsiAwareTextEditorProvider();
    FileEditor fileEditor = editorProvider.createEditor(project, vFile);

    EditorEx editor = (EditorEx)((TextEditor)fileEditor).getEditor();
    editor.getCaretModel().moveToOffset(injectionOffset);
    project.getMessageBus().connect(myFixture.getTestRootDisposable()).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
        @Override
        public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
          if (file.equals(vFile)) editorProvider.disposeEditor(fileEditor);
        }
      });

    JComponent editorComponent = editor.getContentComponent();
    JComponent editorParentComponent = (JComponent)editorComponent.getParent();
    DataManager.registerDataProvider(editorParentComponent, dataId -> {
      return CommonDataKeys.PROJECT.is(dataId) ? project : null;
    });
    return editor;
  }
}
