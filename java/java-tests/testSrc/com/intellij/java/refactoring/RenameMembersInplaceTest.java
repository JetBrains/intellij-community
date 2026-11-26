// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.LightDefaultConstructor;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.JavaNameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class RenameMembersInplaceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final String BASE_PATH = "/refactoring/renameInplace/";

  @Test
  public void testInnerClass() {
    doTestInplaceRename("NEW_NAME");
  }

  @Test
  public void testClassWithConstructorReferenceInside() {
    doTestInplaceRename("NewName");
  }

  @Test
  public void testIncomplete() {
    doTestInplaceRename("Klazz");
  }

  @Test
  public void testConstructor() {
    doTestInplaceRename("Bar");
  }

  @Test
  public void testSuperMethod() {
    doTestInplaceRename("xxx");
  }

  @Test
  public void testMultipleSuperMethods() {
    doTestInplaceRename("x");
  }

  @Test
  public void testUnresolvedMethod() {
    doTestInplaceRename("second");
  }

  @Test
  public void testSuperMethodAnonymousInheritor() {
    doTestInplaceRename("xxx");
  }

  @Test
  public void testDefaultConstructor() {
    doTestInplaceRename("HeadlessHorseman");
  }

  @Test
  public void testDefaultConstructorTwice() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      for (String newName : List.of("SomeClass2", "SomeClass3")) {
        PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
        assertNotNull(element);
        CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), newName, myFixture.getEditor(), element);
      }
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testDefaultConstructorAfterClassNameTyped() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("SomeClass.java", "class SomeClass {}");
    myFixture.runInCommand(() -> {
      PsiClass someClass = ((PsiJavaFile)myFixture.getFile()).getClasses()[0];
      assertNotNull(LightDefaultConstructor.create(someClass));
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> myFixture.getEditor().getDocument().replaceString(6, 15, "Other"));
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiClass otherClass = ((PsiJavaFile)myFixture.getFile()).getClasses()[0];
      PsiMethod constructor = LightDefaultConstructor.create(otherClass);
      assertNotNull(constructor);
      assertEquals("Other", constructor.getName());
    });
  }

  @Test
  public void testMultipleConstructors() {
    doTestInplaceRename("Bar");
  }

  @Test
  public void testClassWithMultipleConstructors() {
    doTestInplaceRename("Bar");
  }

  @Test
  public void testTypeParameterUsedInJavadoc() {
    doTestInplaceRename("K");
  }

  @Test
  public void testMethodWithJavadocRef() {
    doTestInplaceRename("bar");
  }

  @Test
  public void testMethodWithJavadocRef2() {
    doTestInplaceRename("set");
  }

  @Test
  public void testMethodWithJavadocRef3() {
    doTestInplaceRename("set");
  }

  @Test
  public void testMethodWithJavadocRef4() {
    doTestInplaceRename("set");
  }

  @Test
  public void testEnumConstructor() {
    doTestInplaceRename("Bar");
  }

  @Test
  public void testMethodWithMethodRef() {
    doTestInplaceRename("bar");
  }

  @Test
  public void testRecordImplementsInterface() {
    doTestInplaceRename("newValue");
  }

  @Test
  public void testRecordAccessorOverride() {
    doTestInplaceRename("x");
  }

  @Test
  public void testMethodChain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");

    myFixture.runInCommand(() -> {
      final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
      assertNotNull(element);

      TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());

      int start = myFixture.getEditor().getSelectionModel().getSelectionStart();
      int end = myFixture.getEditor().getSelectionModel().getSelectionEnd();

      new MemberInplaceRenameHandler().doRename(element, myFixture.getEditor(), DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent()));
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      assertNotNull(state);

      WriteCommandAction.writeCommandAction(myFixture.getProject())
        .run(() -> myFixture.getEditor().getDocument().replaceString(start, end, "bar"));
      state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      assertNotNull(state);
      state.gotoEnd(false);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testRenameFieldInIncompleteStatement() {
    doTestInplaceRename("bar");
  }

  @Test
  public void testSameNamedMethodsInOneFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");

    myFixture.runInCommand(() -> {
      final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
      assertNotNull(element);

      Editor editor = myFixture.getEditor();
      Project project = editor.getProject();
      TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
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

      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testNameSuggestion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");

    myFixture.runInCommand(() -> {
      final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
      assertNotNull(element);

      final Set<String> result = new LinkedHashSet<>();
      new JavaNameSuggestionProvider().getSuggestedNames(element, myFixture.getFile(), result);

      CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), result.iterator().next(), myFixture.getEditor(), element);

      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testConflictingMethodName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      doTestInplaceRename("bar");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>bar()</code></b> is already defined in class <b><code>Foo</code></b>", e.getMessage());
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
      return;
    }
    fail("Conflict was not detected");
  }

  @Test
  public void testNearParameterHint() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      int originalCaretPosition = myFixture.getEditor().getCaretModel().getOffset();
      Inlay<?> inlay = EditorTestUtil.addInlay(myFixture.getEditor(), originalCaretPosition);
      VisualPosition inlayPosition = inlay.getVisualPosition();
      // make sure caret is to the right of inlay initially
      myFixture.getEditor().getCaretModel().moveToVisualPosition(new VisualPosition(inlayPosition.line, inlayPosition.column + 1));

      final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
      assertNotNull(element);

      TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
      new MemberInplaceRenameHandler().doRename(element, myFixture.getEditor(), DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent()));
      assertEquals(originalCaretPosition, myFixture.getEditor().getCaretModel().getOffset());
      assertTrue(inlay.isValid());
      assertEquals(inlayPosition, inlay.getVisualPosition());
      // check caret is still to the right
      assertEquals(new VisualPosition(inlayPosition.line, inlayPosition.column + 1), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  private void doTestInplaceRename(String newName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");

    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
      if (element == null) {
        final PsiReference reference = TargetElementUtil.findReference(myFixture.getEditor());
        if (reference != null) element = reference.getElement();
      }
      assertNotNull(element);

      CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), newName, myFixture.getEditor(), element);

      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }
}
