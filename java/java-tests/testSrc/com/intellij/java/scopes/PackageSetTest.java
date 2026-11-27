// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.scopes;

import com.intellij.ide.scopeView.NamedScopeFilter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.projectView.TestSourceBasedFixture;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.projectView.TestSourceBasedFixture.testSourceBasedFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PackageSetTest {
  private final TestFixture<TestSourceBasedFixture> testSourceBasedFixture = testSourceBasedFixture("packageSet", "oneModuleStructure");

  private String myModuleName;
  private DependencyValidationManager myHolder;
  private PackageSetFactory myPackageSetFactory;

  @BeforeEach
  void setUp() {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    myHolder = DependencyValidationManager.getInstance(fixture.getProject());
    PackageSetFactory factory = PackageSetFactory.getInstance();
    myPackageSetFactory = new PackageSetFactory() {
      @Override
      public PackageSet compile(String text) throws ParsingException {
        PackageSet compiled = factory.compile(text);
        // This way we also test that createCopy works well in each test
        return compiled.createCopy();
      }
    };
    WriteAction.runAndWait(() -> {
      final ModifiableModuleModel moduleModel = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
      moduleModel.setModuleGroupPath(fixture.getModule(), new String[]{"GRP"});
      moduleModel.commit();
    });
    myModuleName = fixture.getModule().getName();
  }

  @Test
  public void testResFilePattern() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      final PsiDirectory directory = fixture.getPackageDirectory("pack");
      final VirtualFile resources = fixture.getContentRootDirectory().getVirtualFile().findFileByRelativePath("resources");
      assert resources != null;
      final PsiDirectory resourcesDirectory = fixture.getPsiManager().findDirectory(resources);
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
    });
  }

  @Test
  public void testDirWithSpacesFilePattern() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      final PackageSet packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:src/with space/**");
      final PsiDirectory withSpaceDirectory = fixture.getPackageDirectory("with space");
      final PsiFile [] psiFiles = assertAllFilesIncluded(withSpaceDirectory, packageSet, myHolder);
      assertEquals(1, psiFiles.length);
    });
  }

  @Test
  public void testDirWithDashesFilePattern() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      final PackageSet packageSet = myPackageSetFactory.compile("file[" + myModuleName + "]:src/with-dash/**");
      final PsiDirectory withSpaceDirectory = fixture.getPackageDirectory("with-dash");
      final PsiFile [] psiFiles = assertAllFilesIncluded(withSpaceDirectory, packageSet, myHolder);
      assertEquals(1, psiFiles.length);
    });
  }

  @Test
  public void testIncludeExternalLib() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      NamedScopeFilter filter = createFilter("ext[java 1.7]:java/lang//*");

      PsiFile libraryPsiFile =
        JavaPsiFacade.getInstance(fixture.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(fixture.getProject()))
          .getContainingFile();
      VirtualFile libraryVirtualFile = libraryPsiFile.getVirtualFile();
      assertTrue(filter.accept(libraryVirtualFile));

      VirtualFile dir = fixture.getPackageDirectory("pack").getVirtualFile();
      for (VirtualFile file : dir.getChildren()) {
        assertFalse(filter.accept(file));
      }
      assertFalse(filter.accept(dir));
    });
  }

  @Test
  public void testIncludeDirectoryItself() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      NamedScopeFilter filter = createFilter("file:src/pack//*");
      VirtualFile dir = fixture.getPackageDirectory("pack").getVirtualFile();
      for (VirtualFile file : dir.getChildren()) {
        assertTrue(filter.accept(file));
      }
      assertTrue(filter.accept(dir));
    });
  }

  @Test
  public void testExcludeDirectoryItself() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      NamedScopeFilter filter = createFilter("!file:src/pack//*");
      VirtualFile dir = fixture.getPackageDirectory("pack").getVirtualFile();
      for (VirtualFile file : dir.getChildren()) {
        assertFalse(filter.accept(file));
      }
      assertFalse(filter.accept(dir));
    });
  }

  @NotNull
  private NamedScopeFilter createFilter(@NotNull String pattern) throws Exception {
    PackageSet set = myPackageSetFactory.compile(pattern);
    NamedScope scope = new NamedScope(pattern, set);
    return new NamedScopeFilter(myHolder, scope);
  }

  @Test
  public void testModuleFilePattern() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      final PsiDirectory directory = fixture.getPackageDirectory("pack");

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
    });
  }

  @Test
  public void testNoModuleFilePattern() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      final PsiDirectory directory = fixture.getPackageDirectory("pack");
      final PackageSet packageSet = myPackageSetFactory.compile("file:src/pack/*");
      assertAllFilesIncluded(directory, packageSet, myHolder);
    });
  }

  @Test
  public void testAllInModuleMisc() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();

    ReadAction.runBlocking(() -> {
      PackageSet packageSet = myPackageSetFactory.compile("src[" + myModuleName + "]:*..*");
      PsiDirectory directory = fixture.getPackageDirectory("pack");
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
    });
  }

  @Test
  public void testModuleGroup() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      PackageSet packageSet = myPackageSetFactory.compile("src[group:GRP:" + myModuleName + "]:*..*");
      PsiDirectory directory = fixture.getPackageDirectory("pack");
      PsiFile[] psiFiles = assertAllFilesIncluded(directory, packageSet, myHolder);
      assertEquals(4, psiFiles.length);

      packageSet = myPackageSetFactory.compile("src[group:GRP]:*..*");
      assertAllFilesIncluded(directory, packageSet, myHolder);
    });
  }

  @Test
  public void testRecursivePattern() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ReadAction.runBlocking(() -> {
      PackageSet packageSet = myPackageSetFactory.compile("src[" + myModuleName + "]:pack1..*");
      PsiDirectory directory = fixture.getPackageDirectory("pack1/pack2");
      assertAllFilesIncluded(directory, packageSet, myHolder);

      packageSet = myPackageSetFactory.compile("src[" + myModuleName + "]:pack1.*");
      directory = fixture.getPackageDirectory("pack1");
      assertAllFilesIncluded(directory, packageSet, myHolder);

      directory = fixture.getPackageDirectory("pack1/pack2");
      final PsiFile[] psiFiles = directory.getFiles();
      assertNotNull(psiFiles);
      assertEquals(1, psiFiles.length);
      assertFalse(packageSet.contains(psiFiles[0], myHolder));
    });
  }

  private static PsiFile[] assertAllFilesIncluded(PsiDirectory directory,
                                                  PackageSet packageSet,
                                                  DependencyValidationManager holder) {
    PsiFile[] psiFiles = directory.getFiles();
    assertNotNull(psiFiles);

    for (PsiFile psiFile : psiFiles) {
      assertTrue(packageSet.contains(psiFile, holder), psiFile.getVirtualFile().getPath());
    }
    return psiFiles;
  }
}
