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

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
/**
 * @author peter
 */
class MultipleModuleHighlightingTest extends JavaCodeInsightFixtureTestCase {

  public void "test use original place classpath for reference type resolving"() {
    addTwoModules()

    myFixture.addFileToProject "mod1/Class2.java", '''
public class Class2 {
  public void m1() {}
  public void m2() {}
}
'''

    myFixture.addFileToProject "mod2/Class2.java", '''
public class Class2 {
  public void m1() {}
}
'''
    myFixture.addFileToProject "mod2/Class1.java", '''
public class Class1 {
  public Class2 getClass2() {}
  public Class2 class2Field;
}
'''

    myFixture.configureFromExistingVirtualFile(myFixture.addClass("""
class Class3 {
  {
    new Class1().getClass2().m2();
    new Class1().class2Field.m2();
  }
}

""").containingFile.virtualFile)
    myFixture.checkHighlighting()
  }

  public void "test use original place classpath for new expression type resolving"() {
    addTwoModules()

    myFixture.addFileToProject "mod1/A.java", '''
public class A {
  public void m1();
}
'''

    myFixture.addFileToProject "mod2/A.java", '''
public class A {
  public void m2() {}
}
'''
    myFixture.addFileToProject "mod2/B.java", '''
public class B extends A {
}
'''

    myFixture.configureFromExistingVirtualFile(myFixture.addClass("""
class Class3 {
  {
    new B().m1();
    new B().<error descr="Cannot resolve method 'm2()'">m2</error>();
  }
}

""").containingFile.virtualFile)
    myFixture.checkHighlighting()
  }

  private void addTwoModules() {
    def mod1 = PsiTestUtil.addModule(project, JavaModuleType.moduleType, "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    def mod2 = PsiTestUtil.addModule(project, JavaModuleType.moduleType, "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(myModule, mod1)
    ModuleRootModificationUtil.addDependency(myModule, mod2)
  }
}
