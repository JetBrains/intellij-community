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
package com.intellij.java.psi;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaDirectoryServiceHeavyTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testCreatingEnumInLanguageLevel3Project() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_3);
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_7);
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());

    VirtualFile tempDir = myFixture.getTempDirFixture().getFile("");
    PsiDirectory dir = ReadAction.computeBlocking(() -> myFixture.getPsiManager().findDirectory(tempDir));
    PsiClass createdEnum = EdtTestUtil.runInEdtAndGet(() -> JavaDirectoryService.getInstance().createEnum(dir, "Foo"));
    ReadAction.runBlocking(() -> {
      assertTrue(createdEnum.isEnum());
      assertEquals(LanguageLevel.JDK_1_7, PsiUtil.getLanguageLevel(createdEnum));
    });
  }

  @Test
  public void testEffectiveLanguageLevelWorksForLibrarySourceRoot() throws Exception {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TempDirTestFixtureImpl temp = new TempDirTestFixtureImpl();
    temp.setUp();

    try {
      VirtualFile root = temp.findOrCreateDir("lib");
      PsiTestUtil.addLibrary(myFixture.getModule(), "lib", root.getPath(), new String[]{}, new String[]{""});

      IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_3);
      IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_7);
      IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());

      ReadAction.runBlocking(() -> {
        assertEquals(LanguageLevel.JDK_1_3, JavaDirectoryService.getInstance().getLanguageLevel(myFixture.getPsiManager().findDirectory(root)));
        assertEquals(LanguageLevel.JDK_1_3, JavaPsiImplementationHelper.getInstance(myFixture.getProject()).getEffectiveLanguageLevel(root));
      });
    }
    finally {
      temp.tearDown();
    }
  }

}
