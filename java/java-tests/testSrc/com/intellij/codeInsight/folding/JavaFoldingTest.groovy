/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsImpl
import com.intellij.find.FindManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

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

  public void "test closure folding when an abstract method is not in the direct superclass"() {
    myFoldingSettings.COLLAPSE_CLOSURES = true
    def text = """\
public abstract class AroundTemplateMethod<T> {
  public abstract T execute();
}
private static abstract class SetupTimer<T> extends AroundTemplateMethod<T> {
}
class Test {
    void test() {
     new SetupTimer<Integer>() {
      public Integer execute() {
        return 0;
      }
    };
  }
}
"""

    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    def closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("<Integer>"))
    assertNotNull closureStartFold
    assertFalse closureStartFold.expanded

    assertNotNull closureStartFold.group
    def closureFolds = foldingModel.getGroupedRegions(closureStartFold.group)
    assertNotNull closureFolds
    assertEquals(2, closureFolds.size())
  }
  
  public void "test builder style setter"() {
    myFoldingSettings.COLLAPSE_ACCESSORS = true
    def text = """\
class Foo {
    private String bar;

    public Foo setBar(String bar) {
        this.bar = bar;
        return this;
    }
}
"""

    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    int indexOfBar = text.indexOf("this.bar")
    def accessorStartFold = foldingModel.getCollapsedRegionAtOffset(indexOfBar)
    assertNotNull accessorStartFold
    assertFalse accessorStartFold.expanded
  }

  public void "test closure folding doesn't expand when editing inside"() {
    def text = """\
class Test {
    void test() {
     new Runnable() {
      static final long serialVersionUID = 42L;
      public void run() {
        System.out.println(<caret>);
      }
    };
  }
}
"""

    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    def closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"))
    assertNotNull closureStartFold
    assertFalse closureStartFold.expanded
    assert text.substring(closureStartFold.endOffset).startsWith('System') //one line closure

    myFixture.type('2')
    myFixture.doHighlighting()
    closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"))
    assert closureStartFold
  }

  public void "test closure folding placeholder texts"() {
    myFixture.addClass('interface Runnable2 { void run(); }')
    myFixture.addClass('interface Runnable3 { void run(); }')
    myFixture.addClass('interface Runnable4 { void run(); }')
    myFixture.addClass('abstract class MyAction { public abstract void run(); public void registerVeryCustomShortcutSet() {} }')
    myFixture.addClass('abstract class MyAction2 { public abstract void run(); public void registerVeryCustomShortcutSet() {} }')
    def text = """\
class Test {
  MyAction2 action2;

  void test() {
    Runnable r = new Runnable() {
      public void run() {
        System.out.println();
      }
    };
    new Runnable2() {
      public void run() {
        System.out.println();
      }
    }.run();
    foo(new Runnable3() {
      public void run() {
        System.out.println();
      }
    });
    bar(new Runnable4() {
      public void run() {
        System.out.println();
      }
    });
    new MyAction() {
      public void run() {
        System.out.println();
      }
    }.registerVeryCustomShortcutSet();
    action2 = new MyAction2() {
      public void run() {
        System.out.println();
      }
    }
  }

  void foo(Object o) {}
  void bar(Runnable4 o) {}
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl

    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable(")).placeholderText == '() -> { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2(")).placeholderText == '(Runnable2) () -> { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable3(")).placeholderText == '(Runnable3) () -> { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable4(")).placeholderText == '() -> { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction(")).placeholderText == '(MyAction) () -> { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction2(")).placeholderText == '(MyAction2) () -> { '
  }

  public void "test closure folding when overriding one method of many"() {
    myFixture.addClass('abstract class Runnable { void run() {}; void run2() {} }')
    myFixture.addClass('abstract class Runnable2 { void run() {}; void run2() {} }')
    def text = """\
class Test {
  void test() {
    Runnable r = new Runnable() {
      public void run() {
        System.out.println();
      }
    };
    foo(new Runnable2() {
      public void run2() {
        System.out.println();
      }
    });
  }

  void foo(Object o) {}
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl

    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable("))?.placeholderText == 'run() -> { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2("))?.placeholderText == '(Runnable2) run2() -> { '
  }

  public void "test no closure folding when the method throws an unresolved exception"() {
    def text = """\
class Test {
    void test() { new Runnable() {
      public void run() throws Asadfsdafdfasd {
        System.out.println(<caret>);
      }
    };
  }
}
"""

    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    assert !foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"))
  }

  public void "test no closure folding for synchronized methods"() {
    def text = """\
class Test {
    void test() { new Runnable() {
      public synchronized void run() {
        System.out.println(<caret>);
      }
    };
  }
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    assert !foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"))
  }

  public void testFindInFolding() {
    def text = """\
class Test {
    void test1() {
    }
    void test2() {
        <caret>test1();
    }
}
"""
    configure text
    myFixture.performEditorAction 'CollapseBlock'
    myFixture.editor.caretModel.moveToOffset(text.indexOf('test1'))
    myFixture.performEditorAction 'HighlightUsagesInFile'
    FindManager.getInstance(project).findNextUsageInEditor(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
    assertEquals('test1', myFixture.editor.selectionModel.selectedText)
  }
  
  public void testCustomFolding() {
    myFixture.testFolding("$PathManagerEx.testDataPath/codeInsight/folding/${getTestName(false)}.java");
  }
  
  public void "test move methods"() {
    def initialText = '''\
class Test {
    void test1() {
    }
    
    void test2() {
    }
}
'''
    
    Closure<FoldRegion> fold = { String methodName ->
      def text = myFixture.editor.document.text
      def nameIndex = text.indexOf(methodName)
      def start = text.indexOf('{', nameIndex)
      def end = text.indexOf('}', start) + 1
      def regions = myFixture.editor.foldingModel.allFoldRegions
      for (region in regions) {
        if (region.startOffset == start && region.endOffset == end) {
          return region
        }
      }
      fail("Can't find target fold region for method with name '$methodName'. Registered regions: $regions")
      null
    }

    configure initialText
    def foldingModel = myFixture.editor.foldingModel as FoldingModelEx
    foldingModel.runBatchFoldingOperation {
      fold('test1').expanded = true
      fold('test2').expanded = false
    }

    myFixture.editor.caretModel.moveToOffset(initialText.indexOf('void'))
    myFixture.performEditorAction 'MoveStatementDown'
    CodeFoldingManager.getInstance(project).updateFoldRegions(myFixture.editor)
    assertTrue(fold('test1').expanded)
    assertFalse(fold('test2').expanded)

    myFixture.performEditorAction 'MoveStatementUp'
    CodeFoldingManager.getInstance(project).updateFoldRegions(myFixture.editor)
    assertTrue(fold('test1').expanded)
    assertFalse(fold('test2').expanded)
  }

  public void testUnorderedFoldRegionsRegistration() {
    def text = '01234567'
    configure text
    def foldModel = myFixture.editor.foldingModel as FoldingModelImpl
    foldModel.runBatchFoldingOperation {
      def innerFold = foldModel.addFoldRegion(3, 5, '...')
      def outerFold = foldModel.addFoldRegion(2, 6, '...')
      innerFold.expanded = false
      outerFold.expanded = false
    }
    def folds = foldModel.fetchVisible()
    assertEquals(1, folds.length)
    assertEquals(2, folds[0].startOffset)
    assertEquals(6, folds[0].endOffset)
  }
  
  private def configure(String text) {
    myFixture.configureByText("a.java", text)
    CodeFoldingManagerImpl.getInstance(getProject()).buildInitialFoldings(myFixture.editor);
    def foldingModel = myFixture.editor.foldingModel as FoldingModelEx
    foldingModel.rebuild()
    myFixture.doHighlighting()
  }

  public void "test simple property accessors in one line"() {
    configure """class Foo {
 int field;
 int field2;
 
 int getField()
 {
   return field;
 }
 
 void setField(int f) {
   field = f;
 }
 
 void setField2(int f){field2=f;}

}"""
    PsiClass fooClass = JavaPsiFacade.getInstance(project).findClass('Foo', GlobalSearchScope.allScope(project))
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 5
    
    Closure checkAccessorFolding = { FoldRegion region1, FoldRegion region2, PsiMethod method ->
      assert region1.startOffset == method.parameterList.textRange.endOffset
      assert region1.endOffset == method.body.statements[0].textRange.startOffset
      assert region1.placeholderText == ' { '

      assert region2.startOffset == method.body.statements[0].textRange.endOffset
      assert region2.endOffset == method.textRange.endOffset
      assert region2.placeholderText == ' }'
      assert region1.group == region2.group
    }
    
    checkAccessorFolding(regions[0], regions[1], fooClass.methods[0])
    checkAccessorFolding(regions[2], regions[3], fooClass.methods[1])
    
    assert regions[4].placeholderText == '{...}'
  }

  private def changeFoldRegions(Closure op) {
    myFixture.editor.foldingModel.runBatchFoldingOperationDoNotCollapseCaret(op)
  }
}
