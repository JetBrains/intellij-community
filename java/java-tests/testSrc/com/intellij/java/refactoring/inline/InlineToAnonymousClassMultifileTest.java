// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.inline.InlineToAnonymousClassHandler;
import com.intellij.refactoring.inline.InlineToAnonymousClassProcessor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InlineToAnonymousClassMultifileTest {

  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineToAnonymousClass/multifile/";
  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      LibraryTable libraryTable = model.getModuleLibraryTable();
      Library library = libraryTable.createLibrary("test");
      Library.ModifiableModel libraryModel = library.getModifiableModel();
      String path = BASE_PATH + "/lib/simple.jar";
      VirtualFile libJarLocal = StandardFileSystems.local().findFileByPath(path);
      VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(libJarLocal);
      assertNotNull(jarRoot);
      libraryModel.addRoot(jarRoot, OrderRootType.CLASSES);
      libraryModel.addRoot(VfsUtilCore.pathToUrl(BASE_PATH + "/lib/src"), OrderRootType.SOURCES);
      libraryModel.commit();
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR);
  private final TestFixture<LightMultiFileFixture> multiFile = lightMultiFileFixture(projectFixture, BASE_PATH);

  @Test
  public void testProtectedMember() {   // IDEADEV-18738
    doTest("p1.SubjectWithSuper");
  }

  @Test
  public void testImportForConstructor() {   // IDEADEV-18714
    doTest("p1.ChildCtor");
  }

  @Test
  public void testStaticImports() {   // IDEADEV-18745
    doTest("p1.Inlined");
  }

  @Test
  public void testFromLibrary() {   // IDEADEV-18745
    doTest("p.P");
  }

  private void doTest(String className) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();

    multiFile.get().doTest(() -> {
      PsiClass classToInline = myFixture.findClass(className);
      classToInline = (PsiClass)classToInline.getNavigationElement();
      assertNull(InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
      assertTrue(new InlineToAnonymousClassHandler().canInlineElement(classToInline));
      InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(myFixture.getProject(),
                                                                                      classToInline,
                                                                                      null, false, false, false);
      UsageInfo[] usages = processor.findUsages();
      MultiMap<PsiElement, String> conflicts = processor.getConflicts(usages);
      assertEquals(0, conflicts.size());
      processor.run();
    });
  }
}
