// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation

/**
 * @author peter
 */
class Normal6CompletionTest extends NormalCompletionTestCase {

  void testMakeMultipleArgumentsFinalWhenInInner() {
    configure()
    def item = lookup.items.find { 'a, b' == it.lookupString }
    assert item
    lookup.currentItem = item
    type '\n'
    checkResult()
  }

  void testOverwriteGenericsAfterNew() { doTest('\n') }

  void testExplicitTypeArgumentsWhenParameterTypesDoNotDependOnTypeParameters() { doTest() }

  void testClassNameWithGenericsTab2() { doTest('\t') }

  void testClassNameGenerics() { doTest('\n') }

  void testDoubleExpectedTypeFactoryMethod() throws Throwable {
    configure()
    assertStringItems('Key', 'create', 'create')
    assert LookupElementPresentation.renderElement(myItems[1]).itemText == 'Key.<Boolean>create'
    assert LookupElementPresentation.renderElement(myItems[2]).itemText == 'Key.create'
  }

}
