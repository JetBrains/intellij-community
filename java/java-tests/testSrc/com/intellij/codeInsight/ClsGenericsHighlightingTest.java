/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public abstract class ClsGenericsHighlightingTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;
  private Module myModule;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public ClsGenericsHighlightingTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setTestDataPath(PathManagerEx.getTestDataPath() + "/codeInsight/clsHighlighting");
    JavaModuleFixtureBuilder builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setLanguageLevel(getLanguageLevel());
    myFixture.setUp();
    myModule = builder.getFixture().getModule();
  }

  protected abstract LanguageLevel getLanguageLevel();

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myFixture.tearDown();
    myFixture = null;
    myModule = null;
  }

  protected void doTest() {
    String name = getTestName(false);
    addLibrary(name + ".jar");
    myFixture.configureByFile(name + ".java");
    myFixture.checkHighlighting();
  }

  private void addLibrary(@NotNull final String... libraryPath) {
    ModuleRootModificationUtil.updateModel(myModule, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        LibraryTable libraryTable = model.getModuleLibraryTable();
        Library library = libraryTable.createLibrary("test");

        Library.ModifiableModel libraryModel = library.getModifiableModel();
        for (String annotationsDir : libraryPath) {
          String path = myFixture.getTestDataPath() + "/libs/" + annotationsDir;
          VirtualFile libJarLocal = LocalFileSystem.getInstance().findFileByPath(path);
          assertNotNull(libJarLocal);
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(libJarLocal);
          assertNotNull(jarRoot);
          libraryModel.addRoot(jarRoot, OrderRootType.CLASSES);
        }
        libraryModel.commit();

        String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath());
        model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
      }
    });
  }
}
