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

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.dataFlow.ConstantValueInspection
import com.intellij.codeInspection.dataFlow.DataFlowInspection
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

@CompileStatic
class DataFlowInspectionHeavyTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/"
  }

  void testDifferentAnnotationsWithDifferentLanguageLevels() {
    def module6 = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'mod6', myFixture.tempDirFixture.findOrCreateDir('mod6'))
    IdeaTestUtil.setModuleLanguageLevel(module6, LanguageLevel.JDK_1_6)
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_1_8)
    ModuleRootModificationUtil.addDependency(module, module6)
    ModuleRootModificationUtil.setModuleSdk(module6, ModuleRootManager.getInstance(module).sdk)

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
        @annos.NotNull String f = foo.ObjectUtils.notNull(<weak_warning descr="Value 'a' is always 'null'">a</weak_warning>);
        
        void bar(@annos.NotNull String param) { }
        void goo(@annos.Nullable String param) {
          String p1 = foo.ObjectUtils.notNull(param);
          bar(p1);
        }
      }
      '''
    myFixture.configureFromExistingVirtualFile(testFile.virtualFile)
    myFixture.enableInspections(new ConstantValueInspection())
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
    PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("test"), true)

    myFixture.addFileToProject("test/org/junit/Test.java", """
package org.junit;

public @interface Test {
  Class<? extends Throwable> expected();
}
""")
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("test/Foo.java", """
class Foo {
  @org.junit.Test(expected=RuntimeException.class)
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

  void testTypeQualifierNicknameWithoutDeclarations() {
    myFixture.addClass("package javax.annotation.meta; public @interface TypeQualifierNickname {}")
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture)

    def noJsr305dep = 'noJsr305dep'
    def anotherModule = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, noJsr305dep, myFixture.tempDirFixture.findOrCreateDir(noJsr305dep))
    ModuleRootModificationUtil.setModuleSdk(anotherModule, ModuleRootManager.getInstance(module).sdk)

    def nullableNick = myFixture.addFileToProject("$noJsr305dep/bar/NullableNick.java", DataFlowInspectionTest.barNullableNick())

    // We load AST for anno attribute. In cls usages, this isn't an issue, but for simplicity we're testing with red Java source here
    myFixture.allowTreeAccessForFile(nullableNick.virtualFile)

    myFixture.enableInspections(new DataFlowInspection())
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("TypeQualifierNickname.java", "$noJsr305dep/a.java"))
    myFixture.checkHighlighting(true, false, false)
  }

}
