// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.EdtDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.actionSystem.impl.Utils.createAsyncDataContext;
import static com.intellij.platform.ide.documentation.ActionsKt.DOCUMENTATION_TARGETS;

public final class DocumentationTest extends LightJavaCodeInsightTestCase {

  public void testDocumentationTargetsCanBeObtainedFromEdtContext() {
    doTestDocumentationTargetsCanBeObtainedFromPreCachedContext(false);
  }

  public void testDocumentationTargetsCanBeObtainedFromPreCachedContext2() {
    doTestDocumentationTargetsCanBeObtainedFromPreCachedContext(true);
  }

  private void doTestDocumentationTargetsCanBeObtainedFromPreCachedContext(boolean asyncDataContext) {
    DataContext editorContext = configureJavaEditorAndGetItsContext(false, asyncDataContext, "class <caret>A {}");

    getAndCheckTargets(editorContext);
  }

  public void testInjectedCachingIsNotHarmfulForObtainingTopLevelEditor() {
    DataContext editorContext = configureJavaEditorAndGetItsContext(true, false, "class <caret>A {}");

    Editor editor = CommonDataKeys.EDITOR.getData(editorContext);
    assertInstanceOf(editor, EditorImpl.class);
    Editor editor2 = CommonDataKeys.EDITOR.getData(editorContext);
    assertSame(editor, editor2);

    getAndCheckTargets(editorContext);
  }

  public void testDocumentationTargetsCanBeObtainedFromEdtContextInInjected() {
    doTestDocumentationTargetsCanBeObtainedFromPreCachedContextInInjected(false);
  }

  public void testDocumentationTargetsCanBeObtainedFromPreCachedContextInInjected2() {
    doTestDocumentationTargetsCanBeObtainedFromPreCachedContextInInjected(true);
  }

  private void doTestDocumentationTargetsCanBeObtainedFromPreCachedContextInInjected(boolean asyncDataContext) {
    DataContext editorContext = configureJavaEditorAndGetItsContext(true, asyncDataContext, """
      public class A {
          public static void main(String... args) {
              //language=JAVA
              String x = "class B {static {System.ex<caret>it();}}";
          }
      }""");

    List<DocumentationTarget> targets = getAndCheckTargets(editorContext);

    PsiElementDocumentationTarget singleTarget = (PsiElementDocumentationTarget)targets.get(0);
    assertInstanceOf(singleTarget.getTargetElement(), ClsMethodImpl.class);
  }

  private static @NotNull List<DocumentationTarget> getAndCheckTargets(DataContext dataContext) {
    List<DocumentationTarget> targets = dataContext.getData(DOCUMENTATION_TARGETS);
    assertInstanceOf(assertOneElement(targets), PsiElementDocumentationTarget.class);
    return targets;
  }

  private DataContext configureJavaEditorAndGetItsContext(
    boolean makeContextInjected,
    boolean makeContextAsync,
    String fileText
  ) {
    configureFromFileText("A.java", fileText);
    EditorEx editor = createPsiAwareEditorFromCurrentFile();
    DataContext editorContext = new EdtDataContext(editor.getContentComponent());
    if (makeContextAsync) {
      editorContext = createAsyncDataContext(editorContext);
    }
    if (makeContextInjected) {
      editorContext = AnActionEvent.getInjectedDataContext(editorContext);
    }
    return editorContext;
  }

  @Override
  protected void setupEditorForInjectedLanguage() { }
}
