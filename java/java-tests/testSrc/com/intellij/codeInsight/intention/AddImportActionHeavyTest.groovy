/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention

import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

/**
 * @author peter
 */
class AddImportActionHeavyTest extends JavaCodeInsightFixtureTestCase {

  void "test prefer junit in tests"() {
    myFixture.addClass 'package org.junit; public @interface Before {}'
    myFixture.addClass 'package org.aspectj.lang.annotation; public @interface Before {}'
    PsiTestUtil.addSourceRoot(myModule, myFixture.tempDirFixture.findOrCreateDir('tests'), true)
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject('tests/a.java', '@Befor<caret>e class MyTest {}').virtualFile)
    myFixture.launchAction(myFixture.findSingleIntention("Import class"))
    myFixture.checkResult '''import org.junit.Before;

@Before
class MyTest {}'''
  }

}
