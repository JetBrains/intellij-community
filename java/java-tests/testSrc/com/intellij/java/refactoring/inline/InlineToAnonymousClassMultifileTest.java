// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.RefactoringTestCase;
import com.intellij.refactoring.inline.InlineToAnonymousClassHandler;
import com.intellij.refactoring.inline.InlineToAnonymousClassProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.io.File;

/**
 * @author yole
 */
public class InlineToAnonymousClassMultifileTest extends RefactoringTestCase {
  public void testProtectedMember() throws Exception {   // IDEADEV-18738
    doTest("p1.SubjectWithSuper");
  }

  public void testImportForConstructor() throws Exception {   // IDEADEV-18714
    doTest("p1.ChildCtor");
  }

  public void testStaticImports() throws Exception {   // IDEADEV-18745
    doTest("p1.Inlined");
  }

  public void testFromLibrary() throws Exception {   // IDEADEV-18745
    doTest("p.P");
  }

  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineToAnonymousClass/multifile/" + getTestName(true);
  }

  private void doTest(String className) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    final VirtualFile rootDir = createTestProjectStructure(rootBefore);
    String path = getRoot() + "/lib/simple.jar";
    VirtualFile libJarLocal = LocalFileSystem.getInstance().findFileByPath(path);
    if (libJarLocal != null) {
      ModuleRootModificationUtil.updateModel(myModule, model -> {
        LibraryTable libraryTable = model.getModuleLibraryTable();
        Library library = libraryTable.createLibrary("test");
        Library.ModifiableModel libraryModel = library.getModifiableModel();
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(libJarLocal);
        assertNotNull(jarRoot);
        libraryModel.addRoot(jarRoot, OrderRootType.CLASSES);
        libraryModel.addRoot(VfsUtilCore.pathToUrl(getRoot() + "/lib/src"), OrderRootType.SOURCES);
        libraryModel.commit();
      });
    }

    PsiClass classToInline = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    classToInline = (PsiClass)classToInline.getNavigationElement();
    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(myProject,
                                                                                    classToInline,
                                                                                    null, false, false, false);
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement,String> conflicts = processor.getConflicts(usages);
    assertEquals(0, conflicts.size());
    processor.run();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }
}
