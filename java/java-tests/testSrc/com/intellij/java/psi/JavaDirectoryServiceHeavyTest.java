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

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;

/**
 * @author peter
 */
public class JavaDirectoryServiceHeavyTest extends JavaCodeInsightFixtureTestCase {
  public void testCreatingEnumInLanguageLevel3Project() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_7);

    PsiDirectory dir = getPsiManager().findDirectory(myFixture.getTempDirFixture().getFile(""));
    PsiClass createdEnum = JavaDirectoryService.getInstance().createEnum(dir, "Foo");
    assertTrue(createdEnum.isEnum());
    assertEquals(LanguageLevel.JDK_1_7, PsiUtil.getLanguageLevel(createdEnum));
  }

  public void testEffectiveLanguageLevelWorksForLibrarySourceRoot() throws Exception {
    TempDirTestFixtureImpl temp = new TempDirTestFixtureImpl();
    temp.setUp();

    try {
      VirtualFile root = temp.findOrCreateDir("lib");
      PsiTestUtil.addLibrary(myModule, "lib", root.getPath(), new String[]{}, new String[]{""});

      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
      IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_7);

      assertEquals(LanguageLevel.JDK_1_3, JavaDirectoryService.getInstance().getLanguageLevel(getPsiManager().findDirectory(root)));
      assertEquals(LanguageLevel.JDK_1_3, JavaPsiImplementationHelper.getInstance(getProject()).getEffectiveLanguageLevel(root));
    }
    finally {
      temp.tearDown();
    }
  }

}
