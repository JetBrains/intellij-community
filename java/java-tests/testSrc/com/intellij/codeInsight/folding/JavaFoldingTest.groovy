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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl

/**
 * @author Denis Zhdanov
 * @since 1/17/11 1:00 PM
 */
public class JavaFoldingTest extends LightCodeInsightFixtureTestCase {
  
  def JavaCodeFoldingSettingsImpl myFoldingSettings
  def JavaCodeFoldingSettingsImpl myFoldingStateToRestore
  
  @Override
  public void setUp() {
    super.setUp()
    myFoldingSettings = JavaCodeFoldingSettings.instance as JavaCodeFoldingSettingsImpl
    myFoldingStateToRestore = new JavaCodeFoldingSettingsImpl()
    myFoldingStateToRestore.loadState(myFoldingSettings)
  }

  @Override
  protected void tearDown() {
    myFoldingSettings.loadState(myFoldingStateToRestore)
    super.tearDown()
  }

  public void testEndOfLineComments() {
    myFixture.testFolding("$PathManagerEx.testDataPath/codeInsight/folding/${getTestName(false)}.java");
  }

  public void testEditingImports() {
    configure """\
import java.util.List;
import java.util.Map;
<caret>

class Foo { List a; Map b; }
"""
    
    assert myFixture.editor.foldingModel.getCollapsedRegionAtOffset(10)

    myFixture.type 'import '
    myFixture.doHighlighting()
    assert !myFixture.editor.foldingModel.getCollapsedRegionAtOffset(10)
  }

  public void testJavadocLikeClassHeader() {
    def text = """\
/**
 * This is a header to collapse
 */
import java.util.*;
class Foo { List a; Map b; }
"""
    configure text
    def foldRegion = myFixture.editor.foldingModel.getCollapsedRegionAtOffset(0)
    assert foldRegion
    assertEquals 0, foldRegion.startOffset
    assertEquals text.indexOf("import") - 1, foldRegion.endOffset
  }

  public void testSubsequentCollapseBlock() {
    def text = """\
class Test {
    void test(int i) {
        if (i > 1) {
            <caret>i++;
        }
    }
}
"""
    configure text
    myFixture.performEditorAction 'CollapseBlock'
    myFixture.performEditorAction 'CollapseBlock'
    assertEquals(text.indexOf('}', text.indexOf('i++')), myFixture.editor.caretModel.offset)
  }

  public void testFoldGroup() {
    // Implied by IDEA-79420
    myFoldingSettings.COLLAPSE_CLOSURES = true
    def text = """\
class Test {
    void test() {
        new Runnable() {
            public void run() {
                int i = 1;
            }
        }.run();
    }
}
"""
    
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    def closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"))
    assertNotNull closureStartFold
    assertFalse closureStartFold.expanded
    
    assertNotNull closureStartFold.group
    def closureFolds = foldingModel.getGroupedRegions(closureStartFold.group)
    assertNotNull closureFolds
    assertEquals(2, closureFolds.size())
    
    def closureEndFold = closureFolds.get(1)
    assertFalse closureEndFold.expanded
    
    myFixture.editor.caretModel.moveToOffset(closureEndFold.startOffset + 1)
    assertTrue closureStartFold.expanded
    assertTrue closureEndFold.expanded
    
    changeFoldRegions { closureStartFold.expanded = false }
    assertTrue closureStartFold.expanded
    assertTrue closureEndFold.expanded
  }
  
  private def configure(String text) {
    myFixture.configureByText("a.java", text)
    CodeFoldingManagerImpl.getInstance(getProject()).buildInitialFoldings(myFixture.editor);
    myFixture.doHighlighting()
  }

  private def changeFoldRegions(Closure op) {
    myFixture.editor.foldingModel.runBatchFoldingOperationDoNotCollapseCaret(op)
  }
}
