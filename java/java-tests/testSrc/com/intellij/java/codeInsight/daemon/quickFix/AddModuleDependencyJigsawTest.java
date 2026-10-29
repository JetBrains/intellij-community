// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.concurrency.Promise;

import java.io.IOException;

public class AddModuleDependencyJigsawTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_11);
    moduleBuilder.addJdkVersion(LanguageLevel.JDK_11);
  }

  public void testServiceAddsRequiresBeforeThePromiseResolves() throws IOException {
    Module dep = addJigsawModule();
    PsiFile moduleInfo = myFixture.addFileToProject("module-info.java", "module main { }");

    Promise<Void> promise = JavaProjectModelModificationService.getInstance(getProject())
      .addDependency(getModule(), dep, DependencyScope.COMPILE, false);
    Ref<String> moduleInfoOnSuccess = new Ref<>();
    promise.onSuccess(_ -> moduleInfoOnSuccess.set(moduleInfo.getText()));
    PlatformTestUtil.assertPromiseSucceeds(promise);

    assertTrue(ModuleRootManager.getInstance(getModule()).isDependsOn(dep));
    assertTrue(moduleInfoOnSuccess.get(), moduleInfoOnSuccess.get().contains("requires dep;"));
  }

  public void testServiceResolvesWithoutModuleInfo() throws IOException {
    Module dep = addModule();

    Promise<Void> promise = JavaProjectModelModificationService.getInstance(getProject())
      .addDependency(getModule(), dep, DependencyScope.COMPILE, false);
    PlatformTestUtil.assertPromiseSucceeds(promise);

    assertTrue(ModuleRootManager.getInstance(getModule()).isDependsOn(dep));
  }

  public void testQuickFixImportsTheClassAfterRequires() throws IOException {
    addJigsawModule();
    PsiFile moduleInfo = myFixture.addFileToProject("module-info.java", "module main { }");
    myFixture.configureByText("C.java", "package q; class C { <caret>A a; }");

    IntentionAction intention = myFixture.findSingleIntention("Add dependency on module 'dep'");
    myFixture.launchAction(intention);
    PlatformTestUtil.waitWithEventsDispatching("The fix did not import p.A", () -> myFixture.getFile().getText().contains("import p.A;"), 10);

    assertTrue(moduleInfo.getText(), moduleInfo.getText().contains("requires dep;"));
  }

  public void testUndoRevertsTheRequiresDirectiveTogetherWithTheImport() throws IOException {
    addJigsawModule();
    PsiFile moduleInfo = myFixture.addFileToProject("module-info.java", "module main {\n}");
    myFixture.configureByText("C.java", "package q;\n\nclass C {\n  <caret>A a;\n}");

    IntentionAction intention = myFixture.findSingleIntention("Add dependency on module 'dep'");
    myFixture.launchAction(intention);
    PlatformTestUtil.waitWithEventsDispatching("The fix did not import p.A", () -> myFixture.getFile().getText().contains("import p.A;"), 10);
    assertEquals("module main {\n    requires dep;\n}", moduleInfo.getText());

    TestDialogManager.setTestDialog(_ -> Messages.YES);
    try {
      FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor();
      UndoManager.getInstance(getProject()).undo(editor);
    }
    finally {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertFalse(myFixture.getFile().getText(), myFixture.getFile().getText().contains("import p.A;"));
    assertEquals("module main {\n}", moduleInfo.getText());
  }

  private Module addJigsawModule() throws IOException {
    Module module = addModule();
    myFixture.addFileToProject("dep/module-info.java", "module dep { exports p; }");
    myFixture.addFileToProject("dep/p/A.java", "package p; public class A {}");
    return module;
  }

  private Module addModule() throws IOException {
    Module module =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "dep", myFixture.getTempDirFixture().findOrCreateDir("dep"));
    ModuleRootModificationUtil.setModuleSdk(module, ModuleRootManager.getInstance(getModule()).getSdk());
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_11);
    return module;
  }
}
