/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding;


import com.intellij.codeInsight.folding.impl.CodeFoldingManagerImpl
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase

/**
 * @author Denis Zhdanov
 * @since 1/17/11 1:00 PM
 */
public class JavaFoldingTest extends CodeInsightFixtureTestCase {
  
  public void testEndOfLineComments() {
    myFixture.testFolding("$PathManagerEx.testDataPath/codeInsight/folding/${getTestName(false)}.java");
  }

  public void testEditingImports() {
    myFixture.configureByText "a.java", """\
import java.util.List;
import java.util.Map;
<caret>

class Foo { List a; Map b; }
"""

    CodeFoldingManagerImpl.getInstance(getProject()).buildInitialFoldings(myFixture.editor);
    myFixture.doHighlighting()
    assert myFixture.editor.foldingModel.getCollapsedRegionAtOffset(10)

    myFixture.type 'import '
    myFixture.doHighlighting()
    assert !myFixture.editor.foldingModel.getCollapsedRegionAtOffset(10)
  }


}
