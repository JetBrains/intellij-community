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
package com.intellij.java.codeInspection

import com.intellij.codeInspection.dataFlow.DataFlowInspection
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
/**
 * @author peter
 */
class DataFlowInspectionHeavyTest extends JavaCodeInsightFixtureTestCase {

  void testDifferentAnnotationsWithDifferentLanguageLevels() {
    def module6 = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'mod6', myFixture.tempDirFixture.findOrCreateDir('mod6'))
    IdeaTestUtil.setModuleLanguageLevel(module6, LanguageLevel.JDK_1_6)
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_8)
    ModuleRootModificationUtil.addDependency(myModule, module6)
    ModuleRootModificationUtil.setModuleSdk(module6, ModuleRootManager.getInstance(myModule).sdk)

    myFixture.addFileToProject 'mod6/annos/annos.java', annotationsText("ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE")
    myFixture.addFileToProject 'mod6/foo/ObjectUtils.java', '''
      package foo;
      public class ObjectUtils {
        @annos.NotNull
        public static native <T> T notNull(@annos.Nullable T value);
      }
      '''

    myFixture.addFileToProject 'annos/annos.java', annotationsText("ElementType.TYPE_USE")
    DataFlowInspection8Test.setCustomAnnotations(project, myFixture.testRootDisposable, 'annos.NotNull', 'annos.Nullable')

    def testFile = myFixture.addFileToProject 'test.java', '''
      class Zoo {
        @annos.Nullable String a = null;
        @annos.NotNull String f = foo.ObjectUtils.notNull(<warning descr="Value 'a' is always 'null'">a</warning>);
        
        void bar(@annos.NotNull String param) { }
        void goo(@annos.Nullable String param) {
          String p1 = foo.ObjectUtils.notNull(param);
          bar(p1);
        }
      }
      '''
    myFixture.configureFromExistingVirtualFile(testFile.virtualFile)
    myFixture.enableInspections(new DataFlowInspection())
    myFixture.checkHighlighting()
  }

  private static String annotationsText(String targets) {
    """
      package annos;
      import java.lang.annotation.*;
      
      @Target({$targets}) 
      public @interface NotNull {}
      
      @Target({$targets}) 
      public @interface Nullable {}
      """
  }

  void "test no always failing calls in tests"() {
    PsiTestUtil.addSourceRoot(myModule, myFixture.tempDirFixture.findOrCreateDir("test"), true)

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("test/Foo.java", """
class Foo {
  void foo() {
    assertTrue(false);
  }
  private void assertTrue(boolean b) {
    if (!b) throw new RuntimeException();
  }
}
""").virtualFile)
    myFixture.enableInspections(new DataFlowInspection())
    myFixture.checkHighlighting()
  }
}
