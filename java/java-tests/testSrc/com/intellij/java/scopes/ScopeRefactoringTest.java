// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.scopes;

import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.RefactoringScopeElementListenerProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.intellij.psi.search.scope.packageSet.UnionPackageSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ScopeRefactoringTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testClassRename() {
    doTestClassRename("src:p.A", "src:p.B");
  }

  @Test
  public void testUnionPattern() {
    doTestClassRename("src:p.A||src:p.X", "src:p.B||src:p.X");
  }

  @Test
  public void testDoNotRenameEntryInDifferentPackage() {
    doTestClassRename("src:x.p.A||src:p.A", "src:x.p.A||src:p.B");
  }

  @Test
  public void testClassRenameWhenFilePatternIsUsed() {
    doTestClassRename("file:p/A.java", "file:p/B.java");
  }

  private void doTestClassRename(String oldPattern, String newPattern) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass psiClass = myFixture.addClass("package p; public class A {}");
    final VirtualFile file = PsiUtilCore.getVirtualFile(psiClass);
    assertNotNull(file);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.openFileInEditor(file));
    final String scopeName = "myscope";
    PackageSet packageSet;
    try {
      packageSet = PackageSetFactory.getInstance().compile(oldPattern);
    }
    catch (ParsingException e) {
      throw new RuntimeException(e);
    }
    NamedScope namedScope = new NamedScope(scopeName, packageSet);
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(myFixture.getProject());
    validationManager.addScope(namedScope);
    WriteCommandAction.runWriteCommandAction(
      myFixture.getProject(),
      () -> RenameUtil.doRename(psiClass, "B", UsageInfo.EMPTY_ARRAY, myFixture.getProject(),
                                new RefactoringScopeElementListenerProvider().getListener(psiClass)));
    namedScope = validationManager.getScope(scopeName);
    assertNotNull(namedScope);
    PackageSet value = namedScope.getValue();
    assertNotNull(value);
    String valueText = value.getText();
    assertEquals(newPattern, valueText);

    final FileEditor selectedEditor = FileEditorManager.getInstance(myFixture.getProject()).getSelectedEditor(file);
    EdtTestUtil.runInEdtAndWait(() -> UndoManager.getInstance(myFixture.getProject()).undo(selectedEditor));
    namedScope = validationManager.getScope(scopeName);
    assertNotNull(namedScope);
    value = namedScope.getValue();
    assertNotNull(value);
    valueText = value.getText();
    assertEquals(oldPattern, valueText);
    validationManager.setScopes(NamedScope.EMPTY_ARRAY);
  }

  @Test
  public void testRenameModule() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String path = FileUtil.createTempDirectory("renameModuleInScope", null).getAbsolutePath();
    Module module = WriteAction.computeAndWait(
      () -> ModuleManager.getInstance(myFixture.getProject()).newModule(path + "/m.iml", EmptyModuleType.EMPTY_MODULE));
    String scopeName = "module";
    NamedScopeManager.getInstance(myFixture.getProject()).addScope(new NamedScope(scopeName, new FilePatternPackageSet(module.getName(), "*/")));

    String newName = "newModuleName";
    ModuleGroupTestsKt.renameModule(module, newName);
    NamedScope scope = NamedScopeManager.getInstance(myFixture.getProject()).getScope(scopeName);
    assertEquals(newName, assertInstanceOf(FilePatternPackageSet.class, scope.getValue()).getModulePattern());
  }

  @Test
  public void testRenameModuleInCompositeScope() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String path = FileUtil.createTempDirectory("renameModuleInScope", null).getAbsolutePath();
    Module module = WriteAction.computeAndWait(
      () -> ModuleManager.getInstance(myFixture.getProject()).newModule(path + "/m.iml", EmptyModuleType.EMPTY_MODULE));
    String scopeName = "module";
    NamedScopeManager.getInstance(myFixture.getProject()).addScope(new NamedScope(scopeName,
                                                                        UnionPackageSet.create(
                                                                          new FilePatternPackageSet(module.getName(), "*/a.txt"),
                                                                          new FilePatternPackageSet(module.getName(), "*/b.txt"))));

    String newName = "newModuleName";
    ModuleGroupTestsKt.renameModule(module, newName);
    NamedScope scope = NamedScopeManager.getInstance(myFixture.getProject()).getScope(scopeName);
    PackageSet firstSet = assertInstanceOf(UnionPackageSet.class, scope.getValue()).getSets()[0];
    PackageSet secondSet = assertInstanceOf(UnionPackageSet.class, scope.getValue()).getSets()[1];
    assertEquals(newName, assertInstanceOf(FilePatternPackageSet.class, firstSet).getModulePattern());
    assertEquals(newName, assertInstanceOf(FilePatternPackageSet.class, secondSet).getModulePattern());
  }
}
