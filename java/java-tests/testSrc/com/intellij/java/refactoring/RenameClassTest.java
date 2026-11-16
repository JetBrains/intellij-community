// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameJavaImplicitClassProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RenameClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameClass/");

  @Test
  public void testNonJava() {
    doTest("pack1.Class1", "Class1New");
  }

  @Test
  public void testCollision() {
    doTest("pack1.MyList", "List");
  }

  @Test
  public void testCollisionOnCurrentPackage() {
    doTest("pack1.MyList", "List2");
  }

  @Test
  public void testCollisionOnJavaLang() {
    doTest("pack1.String2", "String");
  }

  @Test
  public void testCollisionWithModuleImport() {
    doTest("pack1.MyList", "List");
  }

  @Test
  public void testCollisionWithImplicitClass() {
    doTest("pack1.MyList", "List");
  }

  @Test
  public void testPackageClassConflict() {
    doTest("Fabric.AFabric", "MetaFabric");
  }

  @Test
  public void testInnerClass() {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }
  
  @Test
  public void testInnerClassConflicting() {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }

  @Test
  public void testImport() {
    //noinspection SpellCheckingInspection
    doTest("a.Blubfoo", "BlubFoo");
  }

  @Test
  public void testInSameFile() {
    doTest("Two", "Object");
  }

  @Test
  public void testConstructorJavadoc() {
    doTest("Test", "Test1");
  }

  @Test
  public void testCollision1() {
    doTest("Loader", "Reader");
  }
  
  @Test
  public void testCollision2() {
    doTest("Loader", "List");
  }

  @Test
  public void testImplicitReferenceToDefaultCtr() {
    doTest("pack1.Parent", "ParentXXX");
  }

  @Test
  public void testImplicitlyImported() {
    doTest("pack1.A", "Object");
  }

  @Test
  public void testAutomaticRenameVars() {
    doRenameClass("XX", "Y");
  }

  @Test
  public void testAutomaticRenameLambdaParams() {
    doRenameClass("Bar", "Baz");
  }

  @Test
  public void testAnnotatedReference() {
    doRenameClass("test.MyList", "MyList123");
  }

  @Test
  public void testParameterizedQualifier() {
    doRenameClass("foo.Outer.Inner", "Inner1");
  }

  @Test
  public void testUndoRedoRenameRefactoringListener(){
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    MessageBusConnection connection = myFixture.getProject().getMessageBus().connect();
    Disposer.register(myFixture.getTestRootDisposable(), connection);

    List<String> events = new ArrayList<>();
    connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, new RefactoringEventListener() {
      @Override
      public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
        events.add(afterData != null ? "done" : "done without data");
      }

      @Override
      public void undoRefactoring(@NotNull String refactoringId) {
        events.add("undo");
      }

      @Override
      public void redoRefactoring(@NotNull String refactoringId) {
        events.add("redo");
      }
    });

    doUndoRedoRenameClass("foo.AnotherTestClass", "AnotherTestClass1");

    assertEquals(List.of("done", "undo", "redo"), events);
  }

  @Test
  public void testImplicitClassAsFile() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      VirtualFile fileInTempDir = myFixture.findFileInTempDir("pack1/ImplicitClass.java");
      Document document = FileDocumentManager.getInstance().getDocument(fileInTempDir);
      PsiFile psiFile = PsiDocumentManager.getInstance(myFixture.getProject()).getPsiFile(document);

      assertTrue(psiFile instanceof PsiJavaFile);

      RenameJavaImplicitClassProcessor processor = new RenameJavaImplicitClassProcessor();
      RenameDialog dialog = processor.createRenameDialog(myFixture.getProject(), psiFile, null, null);
      String[] names = dialog.getSuggestedNames();
      assertArrayEquals(new String[]{"ImplicitClass"}, names);

      UiInterceptors.register(new UiInterceptors.UiInterceptor<>
        (RenameJavaImplicitClassProcessor.RenameJavaImplicitClassRenameDialog.class) {
        @Override
        protected void doIntercept(@NotNull RenameJavaImplicitClassProcessor.RenameJavaImplicitClassRenameDialog component) {
          Disposer.register(myFixture.getTestRootDisposable(), component.getDisposable());
          if (component.getNameSuggestionsField().getFocusableComponent() instanceof EditorTextField field) {
            field.setText("ImplicitClass2");
          }
        }
      });

      dialog.show();

      String name = dialog.getNewName();
      assertEquals("ImplicitClass2.java", name);

      PsiElementRenameHandler.rename(psiFile, myFixture.getProject(), null, null, name, processor);
    });
  }

  private void rename(String className, String newName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    PsiClass aClass = myFixture.findClass(className);
    assertNotNull(aClass, "Class XX not found");
    rename(aClass, newName, true);
  }

  /**
   * Renames {@code element}, searching in comments and in text occurrences, with every automatic renamer if {@code withRenamers}; a
   * subclass renames with another engine.
   */
  protected void rename(@NotNull PsiElement element, @NotNull String newName, boolean withRenamers) {
    final RenameProcessor processor = new RenameProcessor(element.getProject(), element, newName, true, true);
    if (withRenamers) {
      for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
        processor.addRenamerFactory(factory);
      }
    }
    processor.run();
  }

  private void doRenameClass(String className, String newName) {
    multiFile.get().doTest(() -> {
      rename(className, newName);
    });
  }

  private void doUndoRedoRenameClass(String className, String newName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      rename(className, newName);
      TestDialogManager.setTestDialog(message -> Messages.YES);
      FileEditor editor = FileEditorManager.getInstance(myFixture.getProject()).getSelectedEditor();
      UndoManager.getInstance(myFixture.getProject()).undo(editor);
      UndoManager.getInstance(myFixture.getProject()).redo(editor);
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    });
  }

  @Test
  public void testAutomaticRenameInheritors() {
    doRenameClass("MyClass", "MyClass1");
  }

  @Test
  public void testAvoidWeirdInheritorRename() {
    doRenameClass("ConnectionView", "JdbcConnection");
  }

  @Test
  public void testAutomaticRenameVarsCollision() {
    doTest("XX", "Y");
  }

  private void doTest(@NonNls String qClassName, @NonNls String newName) {
    multiFile.get().doTest(() -> this.performAction(qClassName, newName));
  }

  private void performAction(String qClassName, String newName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    PsiClass aClass = myFixture.findClass(qClassName);
    assertNotNull(aClass, "Class " + qClassName + " not found");
    rename(aClass, newName, false);
  }
}
