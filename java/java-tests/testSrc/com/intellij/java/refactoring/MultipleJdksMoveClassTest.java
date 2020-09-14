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

package com.intellij.java.refactoring;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.io.IOException;
import java.util.Collections;

public class MultipleJdksMoveClassTest extends UsefulTestCase {
  private JavaCodeInsightTestFixture myFixture;
  private Module myJava7Module;
  private Module myJava8Module;

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      myJava7Module = null;
      myJava8Module = null;
      super.tearDown();
    }
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setTestDataPath(PathManagerEx.getTestDataPath() + "/refactoring/multipleJdks");
    JavaModuleFixtureBuilder<?> builder7 = projectBuilder.addModule(JavaModuleFixtureBuilder.class).setLanguageLevel(LanguageLevel.JDK_1_7);
    JavaModuleFixtureBuilder<?> builder8 = projectBuilder.addModule(JavaModuleFixtureBuilder.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    myFixture.setUp();
    myJava7Module = builder7.getFixture().getModule();
    myJava8Module = builder8.getFixture().getModule();

    ModuleRootModificationUtil.updateModel(myJava7Module, model -> {
      model.setSdk(IdeaTestUtil.getMockJdk17());
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath()) + "/java7";
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
    });

    ModuleRootModificationUtil.updateModel(myJava8Module, model -> {
      model.setSdk(IdeaTestUtil.getMockJdk18());
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath()) + "/java8";
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
    });
  }


  public void testConflictStringUsage() {
    final PsiFile[] files = myFixture.configureByFiles("java7/p/Main.java", "java8/p/Foo.java");
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    RefactoringConflictsUtil.analyzeModuleConflicts(files[0].getProject(), Collections.singletonList(files[0]), 
                                                    UsageInfo.EMPTY_ARRAY, files[1].getVirtualFile(), conflicts);
    
    assertEmpty(conflicts.keySet());
  }

  public void testMoveBetweenDifferentLanguageLevels() throws IOException {
    PsiFile src = myFixture.addFileToProject("java7/p/Some.java", "package p; class Some {}");

    VirtualFile dst = myFixture.getTempDirFixture().findOrCreateDir("java8/p2");
    PsiPackage dstPackage = myFixture.findPackage("p2");

    new MoveFilesOrDirectoriesProcessor(myFixture.getProject(), new PsiElement[]{src.getContainingDirectory()},
                                        assertOneElement(dstPackage.getDirectories()),
                                        true, true, null, null).run();
    FileDocumentManager.getInstance().saveAllDocuments();

    assertEquals("package p2.p; class Some {}", VfsUtilCore.loadText(dst.findChild("p").findChild("Some.java")));
  }
}
