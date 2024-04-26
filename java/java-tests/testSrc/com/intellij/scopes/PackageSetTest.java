// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.scopes;

import com.intellij.ide.scopeView.NamedScopeFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.intellij.testFramework.TestSourceBasedTestCase;
import org.jetbrains.annotations.NotNull;

public class PackageSetTest extends TestSourceBasedTestCase {
  private String myModuleName;
  private DependencyValidationManager myHolder;
  private PackageSetFactory myPackageSetFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myHolder = DependencyValidationManager.getInstance(getProject());
    PackageSetFactory factory = PackageSetFactory.getInstance();
    myPackageSetFactory = new PackageSetFactory() {
      @Override
      public PackageSet compile(String text) throws ParsingException {
        PackageSet compiled = factory.compile(text);
        // This way we also test that createCopy works well in each test
        return compiled.createCopy();
      }
    };
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      moduleModel.setModuleGroupPath(getModule(), new String[]{"GRP"});
      moduleModel.commit();
    });
    myModuleName = getModule().getName();
  }

  @Override
  protected void tearDown() throws Exception {
    myPackageSetFactory = null;
    myHolder = null;
    super.tearDown();
  }

  public void testResFilePattern() throws Exception {
    final PsiDirectory directory = getPackageDirectory("pack");
    final VirtualFile resources = getContentDirectory().getVirtualFile().findFileByRelativePath("resources");
    assert resources != null;
    final PsiDirectory resourcesDirectory = getPsiManager().findDirectory(resources);
    PackageSet packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:**.jsp");
    assert resourcesDirectory != null;
    PsiFile [] psiFiles = resourcesDirectory.getFiles();
    for (PsiFile psiFile : psiFiles) {
      assertFalse(packageSet.contains(psiFile, myHolder));
    }

    packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:*.jsp");
    psiFiles = assertAllFilesIncluded(resourcesDirectory,
                                      packageSet, myHolder) ;
    assertEquals(1, psiFiles.length);

    packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:*//*");
    assertAllFilesIncluded(directory, packageSet, myHolder);
    assertAllFilesIncluded(resourcesDirectory, packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("file:*//*");
    assertAllFilesIncluded(directory, packageSet, myHolder);
    assertAllFilesIncluded(resourcesDirectory, packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("file:*.txt");
    psiFiles = directory.getFiles();
    for (PsiFile psiFile : psiFiles) {
      if (psiFile instanceof PsiPlainTextFile){
        assertTrue(packageSet.contains(psiFile, myHolder));
      } else {
        assertFalse(packageSet.contains(psiFile, myHolder));
      }
    }
  }

  public void testDirWithSpacesFilePattern() throws Exception {
    final PackageSet packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:src/with space/**");
    final PsiDirectory withSpaceDirectory = getPackageDirectory("with space");
    final PsiFile [] psiFiles = assertAllFilesIncluded(withSpaceDirectory, packageSet, myHolder);
    assertEquals(1, psiFiles.length);
  }

  public void testDirWithDashesFilePattern() throws Exception {
    final PackageSet packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:src/with-dash/**");
    final PsiDirectory withSpaceDirectory = getPackageDirectory("with-dash");
    final PsiFile [] psiFiles = assertAllFilesIncluded(withSpaceDirectory, packageSet, myHolder);
    assertEquals(1, psiFiles.length);
  }

  public void testIncludeExternalLib() throws Exception {
    NamedScopeFilter filter = createFilter("ext[java 1.7]:java/lang//*");

    PsiFile libraryPsiFile =
      JavaPsiFacade.getInstance(getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject()))
        .getContainingFile();
    VirtualFile libraryVirtualFile = libraryPsiFile.getVirtualFile();
    assertTrue(filter.accept(libraryVirtualFile));

    VirtualFile dir = getPackageDirectory("pack").getVirtualFile();
    for (VirtualFile file : dir.getChildren()) {
      assertFalse(filter.accept(file));
    }
    assertFalse(filter.accept(dir));
  }

  public void testIncludeDirectoryItself() throws Exception {
    NamedScopeFilter filter = createFilter("file:src/pack//*");
    VirtualFile dir = getPackageDirectory("pack").getVirtualFile();
    for (VirtualFile file : dir.getChildren()) {
      assertTrue(filter.accept(file));
    }
    assertTrue(filter.accept(dir));
  }

  public void testExcludeDirectoryItself() throws Exception {
    NamedScopeFilter filter = createFilter("!file:src/pack//*");
    VirtualFile dir = getPackageDirectory("pack").getVirtualFile();
    for (VirtualFile file : dir.getChildren()) {
      assertFalse(filter.accept(file));
    }
    assertFalse(filter.accept(dir));
  }

  @NotNull
  private NamedScopeFilter createFilter(@NotNull String pattern) throws Exception {
    PackageSet set = myPackageSetFactory.compile(pattern);
    NamedScope scope = new NamedScope(pattern, set);
    return new NamedScopeFilter(myHolder, scope);
  }

  public void testModuleFilePattern() throws Exception {
    final PsiDirectory directory = getPackageDirectory("pack");

    PackageSet packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:src/pack/**.txt||file[" + myModuleName + "]:src/pack/**.java");
    PsiFile[] psiFiles = assertAllFilesIncluded(directory, packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:*.java");
    for (PsiFile psiFile : psiFiles) {
      if (psiFile instanceof PsiJavaFile){
        assertTrue(packageSet.contains(psiFile, myHolder));
      } else {
        assertFalse(packageSet.contains(psiFile, myHolder));
      }
    }
  }

  public void testNoModuleFilePattern() throws Exception {
    final PsiDirectory directory = getPackageDirectory("pack");
    final PackageSet packageSet = myPackageSetFactory.compile("file:src/pack/*");
    assertAllFilesIncluded(directory, packageSet, myHolder);
  }

  public void testAllInModuleMisc() throws Exception {

    PackageSet packageSet = myPackageSetFactory.compile("src[" + myModuleName + "]:*..*");
    PsiDirectory directory = getPackageDirectory("pack");
    PsiFile[] psiFiles = assertAllFilesIncluded(directory, packageSet, myHolder);
    assertEquals(4, psiFiles.length);

    packageSet = myPackageSetFactory.compile("src[" + myModuleName + "*]:*..*");
    assertAllFilesIncluded(directory, packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("src[" + myModuleName + "]:*..*||file[" + myModuleName + "]:**/pack/**.java");
    assertAllFilesIncluded(directory,     packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("(src[" + myModuleName + "]:*..*)||(file[" + myModuleName + "]:**/pack/**.java)");
    assertAllFilesIncluded(directory, packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("((src[" + myModuleName + "]:*..*)&&(file[" + myModuleName + "]:*//*))");
    assertAllFilesIncluded(directory, packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:*//*");
    assertAllFilesIncluded(directory, packageSet, myHolder);
  }

  public void testModuleGroup() throws Exception {
    PackageSet packageSet = myPackageSetFactory.compile("src[group:GRP:" + myModuleName + "]:*..*");
    PsiDirectory directory = getPackageDirectory("pack");
    PsiFile[] psiFiles = assertAllFilesIncluded(directory, packageSet, myHolder);
    assertEquals(4, psiFiles.length);

    packageSet = myPackageSetFactory.compile("src[group:GRP]:*..*");
    assertAllFilesIncluded(directory, packageSet, myHolder);
  }

  public void testRecursivePattern() throws Exception {
    PackageSet packageSet = myPackageSetFactory.compile("src[" + myModuleName + "]:pack1..*");
    PsiDirectory directory = getPackageDirectory("pack1/pack2");
    assertAllFilesIncluded(directory, packageSet, myHolder);

    packageSet = myPackageSetFactory.compile("src[" + myModuleName + "]:pack1.*");
    directory = getPackageDirectory("pack1");
    assertAllFilesIncluded(directory, packageSet, myHolder);

    directory = getPackageDirectory("pack1/pack2");
    final PsiFile[] psiFiles = directory.getFiles();
    assertNotNull(psiFiles);
    assertEquals(1, psiFiles.length);
    assertFalse(packageSet.contains(psiFiles[0], myHolder));
  }

  private static PsiFile[] assertAllFilesIncluded(PsiDirectory directory,
                                                  PackageSet packageSet,
                                                  DependencyValidationManager holder) {
    PsiFile[] psiFiles = directory.getFiles();
    assertNotNull(psiFiles);

    for (PsiFile psiFile : psiFiles) {
      assertTrue(psiFile.getVirtualFile().getPath(), packageSet.contains(psiFile, holder));
    }
    return psiFiles;
  }

  @Override
  protected String getTestPath() {
    return "packageSet";
  }

  @NotNull
  @Override
  protected String getTestDirectoryName() {
    return "oneModuleStructure";
  }
}
