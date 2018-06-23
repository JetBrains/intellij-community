// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve


import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase 
/**
 * @author peter
 */
class LightResolveClassTest extends LightCodeInsightFixtureTestCase {

  void "test no loading for star imported class when named import matches"() {
    def unnamedFile = myFixture.addFileToProject('unnamed/Bar.java', 'package unnamed; public class Bar {}') as PsiFileImpl
    def named = myFixture.addClass 'package named; public class Bar {}'
    def foo = myFixture.addClass '''
import unnamed.*;
import named.Bar;
class Foo extends Bar {}
'''
    
    assertContentsNotLoaded(unnamedFile)
    assert named == foo.superClass
    assertContentsNotLoaded(unnamedFile)
  }

  private static void assertContentsNotLoaded(PsiFileImpl unnamedFile) {
    assert unnamedFile.derefStub() == null
    assert unnamedFile.treeElement == null
  }
}
