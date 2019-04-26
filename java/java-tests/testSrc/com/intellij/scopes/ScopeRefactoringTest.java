// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.scopes;

import com.intellij.module.ModuleGroupTestsKt;
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
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;

import java.io.IOException;

public class ScopeRefactoringTest extends JavaCodeInsightFixtureTestCase {
  public void testClassRename() {
    doTestClassRename("src:p.A", "src:p.B");
  }

  public void testUnionPattern() {
    doTestClassRename("src:p.A||src:p.X", "src:p.B||src:p.X");
  }

  public void testDoNotRenameEntryInDifferentPackage() {
    doTestClassRename("src:x.p.A||src:p.A", "src:x.p.A||src:p.B");
  }

  public void testClassRenameWhenFilePatternIsUsed() {
    doTestClassRename("file:p/A.java", "file:p/B.java");
  }

  private void doTestClassRename(String oldPattern, String newPattern) {
    final PsiClass psiClass = myFixture.addClass("package p; public class A {}");
    final VirtualFile file = PsiUtilCore.getVirtualFile(psiClass);
    assertNotNull(file);
    myFixture.openFileInEditor(file);
    final String scopeName = "myscope";
    PackageSet packageSet;
    try {
      packageSet = PackageSetFactory.getInstance().compile(oldPattern);
    }
    catch (ParsingException e) {
      throw new RuntimeException(e);
    }
    NamedScope namedScope = new NamedScope(scopeName, packageSet);
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(getProject());
    validationManager.addScope(namedScope);
    WriteCommandAction.runWriteCommandAction(
      getProject(),
      () -> RenameUtil.doRename(psiClass, "B", UsageInfo.EMPTY_ARRAY, getProject(),
                                new RefactoringScopeElementListenerProvider().getListener(psiClass)));
    namedScope = validationManager.getScope(scopeName);
    assertNotNull(namedScope);
    PackageSet value = namedScope.getValue();
    assertNotNull(value);
    String valueText = value.getText();
    assertEquals(newPattern, valueText);

    final FileEditor selectedEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor(file);
    UndoManager.getInstance(getProject()).undo(selectedEditor);
    namedScope = validationManager.getScope(scopeName);
    assertNotNull(namedScope);
    value = namedScope.getValue();
    assertNotNull(value);
    valueText = value.getText();
    assertEquals(oldPattern, valueText);
    validationManager.setScopes(NamedScope.EMPTY_ARRAY);
  }

  public void testRenameModule() throws IOException {
    String path = FileUtil.createTempDirectory("renameModuleInScope", null).getAbsolutePath();
    Module module = WriteAction.compute(
      () -> ModuleManager.getInstance(getProject()).newModule(path + "/m.iml", EmptyModuleType.EMPTY_MODULE));
    String scopeName = "module";
    NamedScopeManager.getInstance(getProject()).addScope(new NamedScope(scopeName, new FilePatternPackageSet(module.getName(), "*/")));

    String newName = "newModuleName";
    ModuleGroupTestsKt.renameModule(module, newName);
    NamedScope scope = NamedScopeManager.getInstance(getProject()).getScope(scopeName);
    assertEquals(newName, assertInstanceOf(scope.getValue(), FilePatternPackageSet.class).getModulePattern());
  }

  public void testRenameModuleInCompositeScope() throws IOException {
    String path = FileUtil.createTempDirectory("renameModuleInScope", null).getAbsolutePath();
    Module module = WriteAction.compute(
      () -> ModuleManager.getInstance(getProject()).newModule(path + "/m.iml", EmptyModuleType.EMPTY_MODULE));
    String scopeName = "module";
    NamedScopeManager.getInstance(getProject()).addScope(new NamedScope(scopeName,
                                                                        UnionPackageSet.create(
                                                                          new FilePatternPackageSet(module.getName(), "*/a.txt"),
                                                                          new FilePatternPackageSet(module.getName(), "*/b.txt"))));

    String newName = "newModuleName";
    ModuleGroupTestsKt.renameModule(module, newName);
    NamedScope scope = NamedScopeManager.getInstance(getProject()).getScope(scopeName);
    PackageSet firstSet = assertInstanceOf(scope.getValue(), UnionPackageSet.class).getSets()[0];
    PackageSet secondSet = assertInstanceOf(scope.getValue(), UnionPackageSet.class).getSets()[1];
    assertEquals(newName, assertInstanceOf(firstSet, FilePatternPackageSet.class).getModulePattern());
    assertEquals(newName, assertInstanceOf(secondSet, FilePatternPackageSet.class).getModulePattern());
  }
}
