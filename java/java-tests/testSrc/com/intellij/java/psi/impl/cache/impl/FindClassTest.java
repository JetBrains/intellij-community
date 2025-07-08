// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.util.FindClassUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FindClassTest extends JavaPsiTestCase {
  private VirtualFile myPrjDir1;
  private VirtualFile mySrcDir1;
  private VirtualFile myPackDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      VirtualFile rootVFile =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

      myPrjDir1 = createChildDirectory(rootVFile, "prj1");
      mySrcDir1 = createChildDirectory(myPrjDir1, "src1");

      myPackDir = createChildDirectory(mySrcDir1, "p");
      VirtualFile file1 = createChildData(myPackDir, "A.java");
      setFileText(file1, "package p; public class A{ public void foo(); }");
      VirtualFile file2 = createChildData(myPackDir, "AB.java");
      setFileText(file2, "package p; public class AB { public void foo(); }");
      
      VirtualFile file3 = createChildData(myPackDir, "B.java");
      setFileText(file3, "package p; public class B { public void foo(); }");
      
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      PsiTestUtil.addContentRoot(myModule, myPrjDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
    });
  }

  public void testSimple() {
    PsiClass psiClass = myJavaFacade.findClass("p.A");
    assertEquals("p.A", psiClass.getQualifiedName());
    assertTrue(myJavaFacade.hasClass("p.A", GlobalSearchScope.projectScope(getProject())));
    assertNull(myJavaFacade.findClass("p.X"));
    assertFalse(myJavaFacade.hasClass("p.X", GlobalSearchScope.projectScope(getProject())));
  }

  public void testClassDuplicatedInResourceRoot() {
    WriteAction.run(() -> {
      //duplicate class in resource directory
      VirtualFile resourceDir = createChildDirectory(myPrjDir1, "rSrc1");
      VirtualFile file1 = createChildData(createChildDirectory(resourceDir, "p"), "A.java");
      setFileText(file1, "package p; public class A{ public void foo(); }");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      PsiTestUtil.addSourceRoot(myModule, resourceDir, JavaResourceRootType.RESOURCE);

      PsiClass[] classes = myJavaFacade.findClasses("p.A", GlobalSearchScope.allScope(myProject));
      assertSize(1, classes);
    });
  }

  public void testClassUnderExcludedFolder() {
    WriteAction.run(() -> {
      PsiTestUtil.addExcludedRoot(myModule, myPackDir);

      assertNull(myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject)));

      ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
      final ContentEntry content = rootModel.getContentEntries()[0];
      content.removeExcludeFolder(content.getExcludeFolders()[0]);
      rootModel.commit();
    });
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());

    PsiClass psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
    assertNotNull(psiClass);
    assertEquals("p.A", psiClass.getQualifiedName());
  }

  public void testClassUnderIgnoredFolder() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiClass psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
      assertEquals("p.A", psiClass.getQualifiedName());

      assertTrue(psiClass.isValid());

      FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      String ignoredFilesList = fileTypeManager.getIgnoredFilesList();
      fileTypeManager.setIgnoredFilesList(ignoredFilesList + ";p");
      try {
        assertFalse(psiClass.isValid());
      }
      finally {
        fileTypeManager.setIgnoredFilesList(ignoredFilesList);
      }

      psiClass = myJavaFacade.findClass("p.A");
      assertTrue(psiClass.isValid());
    });
  }

  public void testSynchronizationAfterChange() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      PsiClass psiClass = myJavaFacade.findClass("p.A");
      final VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
      File ioFile = VfsUtilCore.virtualToIoFile(vFile);
      ioFile.setLastModified(5);

      LocalFileSystem.getInstance().refresh(false);

      ModuleRootModificationUtil.setModuleSdk(myModule, null);

      psiClass = myJavaFacade.findClass("p.A");
      assertNotNull(psiClass);
    });
  }

  public void testMultipleModules() {
    List<Module> otherModules = configureTwoMoreModules();
    assertSize(2, otherModules);
    PsiClass psiClass = myJavaFacade.findClass("p.A", getModule().getModuleWithDependenciesAndLibrariesScope(true));
    assertNotNull(psiClass);
    assertEquals("p.A", psiClass.getQualifiedName());

    PsiClass packClass1 = myJavaFacade.findClass("pack.MyClass", getModule().getModuleWithDependenciesAndLibrariesScope(true));
    assertNull(packClass1);

    PsiClass psiClass2 = myJavaFacade.findClass("p.A", otherModules.get(0).getModuleWithDependenciesAndLibrariesScope(true));
    assertNotNull(psiClass2);
    assertEquals("p.A", psiClass2.getQualifiedName());
    assertTrue(myJavaFacade.hasClass("p.A", otherModules.get(0).getModuleWithDependenciesAndLibrariesScope(true)));
    assertFalse(myJavaFacade.hasClass("p.A", otherModules.get(0).getModuleScope()));

    PsiClass packClass2 = myJavaFacade.findClass("pack.MyClass", otherModules.get(0).getModuleWithDependenciesAndLibrariesScope(true));
    assertNotNull(packClass2);
    assertEquals("pack.MyClass", packClass2.getQualifiedName());

    PsiClass psiClass3 = myJavaFacade.findClass("p.A", otherModules.get(1).getModuleWithDependenciesAndLibrariesScope(true));
    assertNull(psiClass3);

    PsiClass packClass3 = myJavaFacade.findClass("pack.MyClass", otherModules.get(1).getModuleWithDependenciesAndLibrariesScope(true));
    assertNull(packClass3);
  }

  public void testFindModulesWithClass() {
    List<Module> otherModules = configureTwoMoreModules();
    assertSize(2, otherModules);

    PsiClass psiClass = myJavaFacade.findClass("p.A", getModule().getModuleWithDependenciesAndLibrariesScope(true));
    assertNotNull(psiClass);
    PsiClass psiClass2 = myJavaFacade.findClass("p.A", otherModules.get(0).getModuleWithDependenciesAndLibrariesScope(true));
    assertNotNull(psiClass2);
    Collection<Module> modules = FindClassUtil.findModulesWithClass(myProject, "p.A");
    assertSameElements(modules, getModule(), otherModules.get(0));

    PsiClass packClass = myJavaFacade.findClass("pack.MyClass", otherModules.get(0).getModuleWithDependenciesAndLibrariesScope(true));
    assertNotNull(packClass);
    Collection<Module> packModules = FindClassUtil.findModulesWithClass(myProject, "pack.MyClass");
    assertSameElements(packModules, otherModules.get(0));
  }

  private List<Module> configureTwoMoreModules() {
    final List<Module> newModules = new ArrayList<>();
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      Module module2 = modifiableModel.newModule("a.iml", JavaModuleType.getModuleType().getId());
      newModules.add(module2);
      ModuleRootModificationUtil.addDependency(module2, getModule());
      File repoLib = new File(PathManagerEx.getTestDataPath(), "/psi/cls/repo/");
      VirtualFile repoRoot = LocalFileSystem.getInstance().findFileByIoFile(repoLib);
      assertNotNull(repoRoot);
      ModuleRootModificationUtil.addModuleLibrary(module2, repoRoot.getUrl());
      newModules.add(modifiableModel.newModule("b.iml", JavaModuleType.getModuleType().getId()));
      modifiableModel.commit();
    });
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    return newModules;
  }

  public void testFindClassInDumbMode() {
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
        assertNotNull(myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject)));
        assertTrue(myJavaFacade.hasClass("p.A", GlobalSearchScope.allScope(myProject)));
        assertNotNull(myJavaFacade.findClass("p.A", new PackageScope(myJavaFacade.findPackage("p"), true, true)));
        
        assertNull(myJavaFacade.findClass("p.X", GlobalSearchScope.allScope(myProject)));
        assertFalse(myJavaFacade.hasClass("p.X", GlobalSearchScope.allScope(myProject)));

        PsiClass bClass = myJavaFacade.findClass("p.B", GlobalSearchScope.allScope(myProject));
        assertNotNull(bClass);
        assertEquals("B", bClass.getName());
      });
    });
  }

}
