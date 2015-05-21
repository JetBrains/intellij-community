/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight

import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
/**
 * @author peter
 */
class NonSourceInspectionTest extends JavaCodeInsightFixtureTestCase {

  public void "test inspection outside source root"() {
    PsiTestUtil.removeAllRoots(myModule, ModuleRootManager.getInstance(myModule).sdk)
    PsiTestUtil.addSourceRoot(myModule, myFixture.tempDirFixture.findOrCreateDir("src"))
    
    myFixture.addFileToProject("src/foo/GenericQuery.java", """
package foo;
public interface GenericQuery<T> {
    public T execute();
}
""")
    def file = myFixture.addFileToProject("SomeClass.java", """
import foo.GenericQuery;
import java.util.Collection;

class SomeClass {
  Collection<User> foo(GenericQuery<Collection<User>> query) {
    return query.execute();
  }
  class User {}
}


""")

    def wrapper = new LocalInspectionToolWrapper(new UncheckedWarningLocalInspection())
    def context = InspectionManager.getInstance(project).createNewGlobalContext(false)
    assertEmpty InspectionEngine.runInspectionOnFile(file, wrapper, context)
  }

  public void "test resolve super constructor reference"() {
    PsiTestUtil.removeAllRoots(myModule, ModuleRootManager.getInstance(myModule).sdk)
    PsiTestUtil.addSourceRoot(myModule, myFixture.tempDirFixture.findOrCreateDir("src"))

    myFixture.addFileToProject("src/Foo.java", """
class Foo<T> {
    public Foo(T x) {
    }
}
""")

    myFixture.configureByText("Foo.java", """
class Foo<T> {
    public Foo(T x) {
    }
}

class Bar extends Foo<String> {
    public Bar() {
        sup<caret>er("a");
    }
}
""")

    assert myFixture.file.physical
    assert assertInstanceOf(myFixture.elementAtCaret, PsiMethod).name == 'Foo'
  }
  
}
