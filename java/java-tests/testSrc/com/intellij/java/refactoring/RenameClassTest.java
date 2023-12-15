// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameJavaImplicitClassProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RenameClassTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameClass/";
  }

  public void testNonJava() {
    doTest("pack1.Class1", "Class1New");
  }

  public void testCollision() {
    doTest("pack1.MyList", "List");
  }

  public void testPackageClassConflict() {
    doTest("Fabric.AFabric", "MetaFabric");
  }

  public void testInnerClass() {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }
  
  public void testInnerClassConflicting() {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }

  public void testImport() {
    //noinspection SpellCheckingInspection
    doTest("a.Blubfoo", "BlubFoo");
  }

  public void testInSameFile() {
    doTest("Two", "Object");
  }

  public void testConstructorJavadoc() {
    doTest("Test", "Test1");
  }

  public void testCollision1() {
    doTest("Loader", "Reader");
  }
  
  public void testCollision2() {
    doTest("Loader", "List");
  }

  public void testImplicitReferenceToDefaultCtr() {
    doTest("pack1.Parent", "ParentXXX");
  }

  public void testImplicitlyImported() {
    doTest("pack1.A", "Object");
  }

  public void testAutomaticRenameVars() {
    doRenameClass("XX", "Y");
  }

  public void testAutomaticRenameLambdaParams() {
    doRenameClass("Bar", "Baz");
  }

  public void testAnnotatedReference() {
    doRenameClass("test.MyList", "MyList123");
  }

  public void testParameterizedQualifier() {
    doRenameClass("foo.Outer.Inner", "Inner1");
  }

  public void testUndoRedoRenameRefactoringListener(){
    MessageBusConnection connection = myFixture.getProject().getMessageBus().connect();
    Disposer.register(myFixture.getTestRootDisposable(), connection);

    RefactoringEventListener listener = mock(RefactoringEventListener.class);
    connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, listener);

    doUndoRedoRenameClass("foo.AnotherTestClass", "AnotherTestClass1");

    verify(listener).refactoringDone(any(String.class), any(RefactoringEventData.class));
    verify(listener).undoRefactoring(any(String.class));
    verify(listener).redoRefactoring(any(String.class));
  }

  public void testImplicitClassAsFile() {
    doTest(() -> {
      VirtualFile fileInTempDir = myFixture.findFileInTempDir("pack1/ImplicitClass.java");
      Document document = FileDocumentManager.getInstance().getDocument(fileInTempDir);
      PsiFile psiFile = PsiDocumentManager.getInstance(myFixture.getProject()).getPsiFile(document);
      PsiElementRenameHandler.rename(psiFile, myFixture.getProject(), null, null, "ImplicitClass2.java", new RenameJavaImplicitClassProcessor());
    });
  }

  private void rename(final String className, final String newName) {
    PsiClass aClass = myFixture.findClass(className);
    assertNotNull("Class XX not found", aClass);

    final RenameProcessor processor = new RenameProcessor(getProject(), aClass, newName, true, true);
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      processor.addRenamerFactory(factory);
    }
    processor.run();
  }

  private void doRenameClass(final String className, final String newName) {
    doTest(() -> {
      rename(className, newName);
    });
  }

  private void doUndoRedoRenameClass(final String className, final String newName) {
    doTest(() -> {
      rename(className, newName);
      TestDialogManager.setTestDialog(message -> Messages.YES);
      FileEditor editor = FileEditorManager.getInstance(myFixture.getProject()).getSelectedEditor();
      UndoManager.getInstance(myFixture.getProject()).undo(editor);
      UndoManager.getInstance(myFixture.getProject()).redo(editor);
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    });
  }

  public void testAutomaticRenameInheritors() {
    doRenameClass("MyClass", "MyClass1");
  }

  public void testAutomaticRenameVarsCollision() {
    doTest("XX", "Y");
  }

  private void doTest(@NonNls final String qClassName, @NonNls final String newName) {
    doTest(() -> this.performAction(qClassName, newName));
  }

  private void performAction(String qClassName, String newName) {
    PsiClass aClass = myFixture.findClass(qClassName);
    assertNotNull("Class " + qClassName + " not found", aClass);

    new RenameProcessor(getProject(), aClass, newName, true, true).run();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
