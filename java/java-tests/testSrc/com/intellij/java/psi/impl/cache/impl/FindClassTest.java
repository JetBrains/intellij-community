/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.util.FindClassUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class FindClassTest extends PsiTestCase {
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
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      PsiTestUtil.addContentRoot(myModule, myPrjDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
    });
  }

  public void testSimple() throws Exception {
    PsiClass psiClass = myJavaFacade.findClass("p.A");
    assertEquals("p.A", psiClass.getQualifiedName());
  }

  public void testClassUnderExcludedFolder() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiTestUtil.addExcludedRoot(myModule, myPackDir);

      PsiClass psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
      assertNull(psiClass);

      ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
      final ContentEntry content = rootModel.getContentEntries()[0];
      content.removeExcludeFolder(content.getExcludeFolders()[0]);
      rootModel.commit();

      psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
      assertEquals("p.A", psiClass.getQualifiedName());
    });
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
      File ioFile = VfsUtil.virtualToIoFile(vFile);
      ioFile.setLastModified(5);

      LocalFileSystem.getInstance().refresh(false);

      ModuleRootModificationUtil.setModuleSdk(myModule, null);

      psiClass = myJavaFacade.findClass("p.A");
      assertNotNull(psiClass);
    });
  }

  public void testMultipleModules() throws Exception {
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

    PsiClass packClass2 = myJavaFacade.findClass("pack.MyClass", otherModules.get(0).getModuleWithDependenciesAndLibrariesScope(true));
    assertNotNull(packClass2);
    assertEquals("pack.MyClass", packClass2.getQualifiedName());

    PsiClass psiClass3 = myJavaFacade.findClass("p.A", otherModules.get(1).getModuleWithDependenciesAndLibrariesScope(true));
    assertNull(psiClass3);

    PsiClass packClass3 = myJavaFacade.findClass("pack.MyClass", otherModules.get(1).getModuleWithDependenciesAndLibrariesScope(true));
    assertNull(packClass3);
  }

  public void testFindModulesWithClass() throws Exception {
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
    final List<Module> newModules = ContainerUtil.newArrayList();
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      Module module2 = modifiableModel.newModule("a.iml", StdModuleTypes.JAVA.getId());
      newModules.add(module2);
      ModuleRootModificationUtil.addDependency(module2, getModule());
      File repoLib = new File(PathManagerEx.getTestDataPath(), "/psi/cls/repo/");
      VirtualFile repoRoot = LocalFileSystem.getInstance().findFileByIoFile(repoLib);
      assertNotNull(repoRoot);
      ModuleRootModificationUtil.addModuleLibrary(module2, repoRoot.getUrl());
      newModules.add(modifiableModel.newModule("b.iml", StdModuleTypes.JAVA.getId()));
      modifiableModel.commit();
    });
    return newModules;
  }

  public void testFindClassInDumbMode() {
    try {
      DumbServiceImpl.getInstance(myProject).setDumb(true);
      DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
        assertNotNull(myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject)));
        assertNotNull(myJavaFacade.findClass("p.A", new PackageScope(myJavaFacade.findPackage("p"), true, true)));
      });
    }
    finally {
      DumbServiceImpl.getInstance(myProject).setDumb(false);
    }
  }

}
