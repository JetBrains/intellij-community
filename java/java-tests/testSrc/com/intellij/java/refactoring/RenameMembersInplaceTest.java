// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.JavaNameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class RenameMembersInplaceTest extends LightJavaCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameInplace/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInnerClass() {
    doTestInplaceRename("NEW_NAME");
  }
  
  public void testClassWithConstructorReferenceInside() {
    doTestInplaceRename("NewName");
  }
  
  public void testIncomplete() {
    doTestInplaceRename("Klazz");
  }
  
  public void testConstructor() {
    doTestInplaceRename("Bar");
  }

  public void testSuperMethod() {
    doTestInplaceRename("xxx");
  }

  public void testUnresolvedMethod() {
    doTestInplaceRename("second");
  }
  
  public void testSuperMethodAnonymousInheritor() {
    doTestInplaceRename("xxx");
  }

  public void testMultipleConstructors() {
    doTestInplaceRename("Bar");
  }

  public void testClassWithMultipleConstructors() {
    doTestInplaceRename("Bar");
  }

  public void testTypeParameterUsedInJavadoc() {
    doTestInplaceRename("K");
  }
  
  public void testMethodWithJavadocRef() {
    doTestInplaceRename("bar");
  }
  
  public void testMethodWithJavadocRef2() {
    doTestInplaceRename("set");
  }

  public void testMethodWithJavadocRef3() {
    doTestInplaceRename("set");
  }

  public void testMethodWithJavadocRef4() {
    doTestInplaceRename("set");
  }
  
  public void testEnumConstructor() {
    doTestInplaceRename("Bar");
  }

  public void testMethodWithMethodRef() {
    doTestInplaceRename("bar");
  }
  
  public void testRecordImplementsInterface() {
    doTestInplaceRename("newValue");
  }

  public void testMethodChain() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    final PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertNotNull(element);

    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    
    int start = getEditor().getSelectionModel().getSelectionStart();
    int end = getEditor().getSelectionModel().getSelectionEnd();
    
    new MemberInplaceRenameHandler().doRename(element, getEditor(), DataManager.getInstance().getDataContext(getEditor().getComponent()));
    TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(state);

    WriteCommandAction.writeCommandAction(getProject())
      .run(() -> getEditor().getDocument().replaceString(start, end, "bar"));
    state = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(state);
    state.gotoEnd(false);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testRenameFieldInIncompleteStatement() {
    doTestInplaceRename("bar");
  }

  public void testSameNamedMethodsInOneFile() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    final PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertNotNull(element);

    Editor editor = getEditor();
    Project project = editor.getProject();
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    new MemberInplaceRenameHandler().doRename(element, editor, DataManager.getInstance().getDataContext(editor.getComponent()));
    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    assert state != null;
    assertEquals(2, state.getSegmentsCount());
    final TextRange range = state.getCurrentVariableRange();
    assert range != null;
    final Editor finalEditor = editor;
    WriteCommandAction.writeCommandAction(project)
                      .run(() -> finalEditor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "newDoSomething "));

    state = TemplateManagerImpl.getTemplateState(editor);
    assert state != null;
    state.gotoEnd(false);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testNameSuggestion() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    final PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertNotNull(element);

    final Set<String> result = new LinkedHashSet<>();
    new JavaNameSuggestionProvider().getSuggestedNames(element, getFile(), result);

    CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), result.iterator().next(), getEditor(), element);

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testConflictingMethodName() {
    try {
      doTestInplaceRename("bar");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method bar() is already defined in the class <b><code>Foo</code></b>", e.getMessage());
      checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
      return;
    }
    fail("Conflict was not detected");
  }

  public void testNearParameterHint() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    int originalCaretPosition = getEditor().getCaretModel().getOffset();
    Inlay inlay = EditorTestUtil.addInlay(getEditor(), originalCaretPosition);
    VisualPosition inlayPosition = inlay.getVisualPosition();
    // make sure caret is to the right of inlay initially
    getEditor().getCaretModel().moveToVisualPosition(new VisualPosition(inlayPosition.line, inlayPosition.column + 1));

    final PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertNotNull(element);

    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    new MemberInplaceRenameHandler().doRename(element, getEditor(), DataManager.getInstance().getDataContext(getEditor().getComponent()));
    assertEquals(originalCaretPosition, getEditor().getCaretModel().getOffset());
    assertTrue(inlay.isValid());
    assertEquals(inlayPosition, inlay.getVisualPosition());
    // check caret is still to the right
    assertEquals(new VisualPosition(inlayPosition.line, inlayPosition.column + 1), getEditor().getCaretModel().getVisualPosition());
  }

  private void doTestInplaceRename(final String newName) {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    if (element == null) {
      final PsiReference reference = TargetElementUtil.findReference(getEditor());
      if (reference != null) element = reference.getElement();
    }
    assertNotNull(element);

    CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), newName, getEditor(), element);

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
