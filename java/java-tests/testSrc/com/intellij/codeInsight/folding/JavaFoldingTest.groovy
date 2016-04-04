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
package com.intellij.codeInsight.folding

import com.intellij.codeInsight.folding.impl.CodeFoldingManagerImpl
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsImpl
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilder
import com.intellij.find.FindManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author Denis Zhdanov
 * @since 1/17/11 1:00 PM
 */
public class JavaFoldingTest extends LightCodeInsightFixtureTestCase {

  def JavaCodeFoldingSettingsImpl myFoldingSettings
  def JavaCodeFoldingSettingsImpl myFoldingStateToRestore

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7
  }

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
    assert !myFixture.editor.foldingModel.getCollapsedRegionAtOffset(46)
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
        System.out.println();
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

    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("();") + 1)
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

    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable(")).placeholderText == '() ' + rightArrow() + ' { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2(")).placeholderText == '(Runnable2) () ' + rightArrow() + ' { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable3(")).placeholderText == '(Runnable3) () ' + rightArrow() + ' { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable4(")).placeholderText == '() ' + rightArrow() + ' { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction(")).placeholderText == '(MyAction) () ' + rightArrow() + ' { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction2(")).placeholderText == '(MyAction2) () ' + rightArrow() + ' { '
  }

  private static String rightArrow() {
    return JavaFoldingBuilder.rightArrow
  }

  public void "test closure folding after paste"() {
    def text = """\
class Test {
<caret>// comment
  void test() {
    Runnable r = new Runnable() {
      public void run() {
        System.out.println();
      }
    };
  }
}
"""
    configure text
    myFixture.performEditorAction("EditorCut")
    myFixture.performEditorAction("EditorPaste")

    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl

    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable(")).placeholderText == '() ' + rightArrow() + ' { '
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

    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable("))?.placeholderText == 'run() ' + rightArrow() + ' { '
    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2("))?.placeholderText == '(Runnable2) run2() ' + rightArrow() + ' { '
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

  public void "test custom folding IDEA-122715 and IDEA-87312"() {
    def text = """\
public class Test {

    //region Foo
    interface Foo {void bar();}
    //endregion

    //region Bar
    void test() {

    }
    //endregion
    enum Bar {
        BAR1,
        BAR2
    }
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    int count = 0;
    for (FoldRegion region : foldingModel.allFoldRegions) {
      if (region.startOffset == text.indexOf("//region Foo")) {
        assert region.placeholderText == "Foo";
        count ++;
      }
      else if (region.startOffset == text.indexOf("//region Bar")) {
        assert region.placeholderText == "Bar"
        count ++;
      }
    }
    assert count == 2 : "Not all custom regions are found";
  }

  public void "test custom foldings intersecting with language ones"() {
    def text = """\
class Foo {
//*********************************************
// region Some
//*********************************************

  int t = 1;

//*********************************************
// endregion
//*********************************************
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
    assertEquals 1, foldRegionsCount
    assertEquals "Some", foldingModel.allFoldRegions[0].placeholderText
  }

  public void "test custom folding collapsed by default"() {
    def text = """\
class Test {
  void test() {
    //<editor-fold desc="Custom region">
    System.out.println(1);
    System.out.println(2);
    //</editor-fold>
    System.out.println(3);
  };
}
"""
    boolean oldValue = CodeFoldingSettings.instance.COLLAPSE_CUSTOM_FOLDING_REGIONS;
    try {
      CodeFoldingSettings.instance.COLLAPSE_CUSTOM_FOLDING_REGIONS = true;
      configure text
      def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl
      assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("//<editor-fold"))
    }
    finally {
      CodeFoldingSettings.instance.COLLAPSE_CUSTOM_FOLDING_REGIONS = oldValue;
    }
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
 int field3;

 int getField()
 {
   return field;
 }

 void setField(int f) {
   field = f;
 }

 void setField2(int f){field2=f;} // normal method folding here

  // normal method folding here
 void setField3(int f){

   field2=f;
 }

}"""
    PsiClass fooClass = JavaPsiFacade.getInstance(project).findClass('Foo', GlobalSearchScope.allScope(project))
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 6

    checkAccessorFolding(regions[0], regions[1], fooClass.methods[0])
    checkAccessorFolding(regions[2], regions[3], fooClass.methods[1])

    assert regions[4].placeholderText == '{...}'
    assert regions[5].placeholderText == '{...}'
  }

  static checkAccessorFolding(FoldRegion region1, FoldRegion region2, PsiMethod method) {
    assert region1.startOffset == method.parameterList.textRange.endOffset
    assert region1.endOffset == method.body.statements[0].textRange.startOffset
    assert region1.placeholderText == ' { '

    assert region2.startOffset == method.body.statements[0].textRange.endOffset
    assert region2.endOffset == method.textRange.endOffset
    assert region2.placeholderText == ' }'
    assert region1.group == region2.group
  }

  public void "test fold one-line methods"() {
    configure """class Foo {
 @Override
 int someMethod() {
   return 0;
 }

 int someOtherMethod(
   int param) {
   return 0;
 }

}"""
    PsiClass fooClass = JavaPsiFacade.getInstance(project).findClass('Foo', GlobalSearchScope.allScope(project))
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 3
    checkAccessorFolding(regions[0], regions[1], fooClass.methods[0])
  }

  public void "test don't inline array methods"() {
    configure """class Foo {
 int arrayMethod(int param)[] {
   return new int[];
 }

}"""
    assert myFixture.editor.foldingModel.allFoldRegions.size() == 1
  }

  public void "test don't inline very long one-line methods"() {
    configure """class Foo {
 int someVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable;

 // don't create folding that would exceed the right margin
 int getSomeVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable() {
   return someVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable;
 }
}"""
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 1
    assert regions[0].placeholderText == '{...}'
  }

  public void "test insert boolean literal argument name"() {
    myFoldingSettings.INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = true;
    def text = """class Groo {

 public void test() {
  boolean testNow = System.currentTimeMillis() > 34000;
  int times = 1;
  float pi = 4;
  String title = "Testing..."
  char ch = 'q'
  File file;

  configure(true, false, 555, 3.141f, "Huge Title", 'c', null);
  configure(testNow, shouldIgnoreRoots(), fourteen, pi, title, c, file);
 }

 pubic void configure(boolean testNow, boolean shouldIgnoreRoots, int times, float pii, String title, char terminate, File file) {
  System.out.println();
  System.out.println();
 }

}"""
    configure text
    PsiClass fooClass = JavaPsiFacade.getInstance(project).findClass('Groo', GlobalSearchScope.allScope(project))

    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 9

    def literals = fooClass.methods[0].body.statements[6].children[0].children[1].children.findAll { it instanceof PsiLiteralExpression }
    def parameters = fooClass.methods[1].parameterList.parameters

    for (int i = 0; i < literals.size(); i++) {
      def currentElement = literals[i]
      def correspondingFolding = regions[i + 1]
      assert correspondingFolding.startOffset == currentElement.textRange.startOffset && correspondingFolding.endOffset == currentElement.textRange.endOffset
      assert correspondingFolding.placeholderText == parameters[i].name + ": " + currentElement.text
    }
  }

  public void "test do not inline name if setter"() {
    def text = """class Groo {

 public void test() {
  setTestNow(false);
  System.out.println("");
 }

 pubic void setTestNow(boolean testNow) {
  System.out.println("");
  System.out.println("");
 }

}"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions
    assert regions.size() == 2
  }

  public void "test do not collapse varargs"() {
    myFoldingSettings.INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = true;
    def text = """
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    testBooleanVarargs(13, false);
  }

  public boolean testBooleanVarargs(int test, boolean... booleans) {
    int temp = test;
    return false;
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 3
    checkRangeOffsetByPositionInText(regions[1], text, "13")
    assert regions[1].placeholderText == "test: 13"
  }

  public void "test do not inline if parameter length is one or two"() {
    def text = """
public class CharSymbol {

  public void main() {
    System.out.println("AAA");
    count(1, false);
  }

  public void count(int t, boolean fa) {
    int temp = test;
    boolean isFast = fast;
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 2
  }

  public void "test do not inline known subsequent parameter names"() {
    def text = """
public class Test {
  public void main() {
    test1(1, 2);
    test2(1, 2);
    test3(1, 2);
    doTest("first", "second");
  }

  public void test1(int first, int second) {
    int start = first;
    int end = second;
  }

  public void test2(int key, int value) {
    int start = key;
    int end = value;
  }

  public void test3(int key, int value) {
    int start = key;
    int end = value;
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions
    assert regions.size() == 4
  }

  public void "test do not inline paired ranged names"() {
    def text = """
public class CharSymbol {

  public void main() {
    String s = "AAA";
    int last = 3;

    substring1(1, last);
    substring2(1, last);
    substring3(1, last);
    substring4(1, last);
  }

  public void substring1(int beginIndex, int endIndex) {
    int start = beginIndex;
    int end = endIndex;
  }

  public void substring2(int startIndex, int endIndex) {
    int start = startIndex;
    int end = endIndex;
  }

  public void substring3(int from, int to) {
    int start = from;
    int end = to;
  }

  public void substring4(int first, int last) {
    int start = first;
    int end = last;
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 5
  }

  public void "test inline names if literal expression can be assigned to method parameter"() {
    myFoldingSettings.INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = true;
    def text = """
public class CharSymbol {

  public void main() {
    Object obj = new Object();
    count(100, false, "Hi!");
  }

  public void count(Integer test, Boolean boo, CharSequence seq) {
    int a = test;
    Object obj = new Object();
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 5

    checkRangeOffsetByPositionInText(regions[1], text, "100")
    assert regions[1].placeholderText == "test: 100"

    checkRangeOffsetByPositionInText(regions[2], text, "false")
    assert regions[2].placeholderText == "boo: false"

    checkRangeOffsetByPositionInText(regions[3], text, '"Hi!"')
    assert regions[3].placeholderText == 'seq: "Hi!"'
  }

  public void "test inline negative and positive numbers"() {
    myFoldingSettings.INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = true;
    def text = """
public class CharSymbol {

  public void main() {
    Object obj = new Object();
    count(-1, obj);
    count(+1, obj);
  }

  public void count(int test, Object obj) {
    Object tmp = obj;
    boolean isFast = false;
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 4

    checkRangeOffsetByPositionInText(regions[1], text, "-1")
    assert regions[1].placeholderText == "test: -1"

    checkRangeOffsetByPositionInText(regions[2], text, "+1")
    assert regions[2].placeholderText == "test: +1"
  }

  public void "test inline constructor literal arguments names"() {
    myFoldingSettings.INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = true;
    def text = """
public class Test {

  public void main() {
    System.out.println("AAA");
    Checker r = new Checker(true, false) {
        @Override
        void test() {
        }
    };
  }

  abstract class Checker {
    Checker(boolean isActive, boolean requestFocus) {}
    abstract void test();
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.length == 6

    assert regions[1].placeholderText == "isActive: true"
    assert regions[2].placeholderText == "requestFocus: false"

    checkRangeOffsetByPositionInText(regions[1], text, "true")
    checkRangeOffsetByPositionInText(regions[2], text, "false")
  }

  public void "test inline anonymous class constructor literal arguments names"() {
    myFoldingSettings.INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = true;
    def text = """
public class Test {

  Test(int counter, boolean shouldTest) {
    System.out.println();
    System.out.println();
  }

  public static void main() {
    System.out.println();
    Test t = new Test(10, false);
  }

}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.length == 4

    assert regions[2].placeholderText == "counter: 10"
    assert regions[3].placeholderText == "shouldTest: false"

    checkRangeOffsetByPositionInText(regions[2], text, "10")
    checkRangeOffsetByPositionInText(regions[3], text, "false")
  }

  private static def checkRangeOffsetByPositionInText(FoldRegion region, String text, String foldElement) {
    assert region.startOffset == text.indexOf(foldElement) && region.endOffset == text.indexOf(foldElement) + foldElement.length()
  }


  private def changeFoldRegions(Closure op) {
    myFixture.editor.foldingModel.runBatchFoldingOperationDoNotCollapseCaret(op)
  }

  public void "test unselect word should go inside folding group"() {
    configure """class Foo {
 int field;

 int getField() {
   return field;
 }

}"""
    assertSize 2, myFixture.editor.foldingModel.allFoldRegions
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("return"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
    assert """int getField() {
   return field;
 }""" == myFixture.editor.selectionModel.selectedText

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET)
    assert 'return field;' == myFixture.editor.selectionModel.selectedText
  }

  public void "test expand and collapse regions in selection"() {
    def text = """
class Foo {
    public static void main() {
        new Runnable(){
            public void run() {
            }
        }.run();
    }
}
"""
    configure text
    assertEquals 3, foldRegionsCount
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_REGIONS)
    assertEquals 3, expandedFoldRegionsCount


    myFixture.editor.selectionModel.setSelection(text.indexOf("new"), text.indexOf("run();"))
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS)
    assertEquals 1, expandedFoldRegionsCount
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_REGIONS)
    assertEquals 3, expandedFoldRegionsCount
  }

  public void "test expand and collapse recursively"() {
    def text = """
class Foo {
    public static void main() {
        new Runnable(){
            public void run() {
            }
        }.run();
    }
}
"""
    configure text
    assertEquals 3, foldRegionsCount
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_REGIONS)
    assertEquals 3, expandedFoldRegionsCount


    myFixture.editor.caretModel.moveToOffset(text.indexOf("new"))
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_REGION_RECURSIVELY)
    assertEquals 1, expandedFoldRegionsCount
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_REGION_RECURSIVELY)
    assertEquals 3, expandedFoldRegionsCount
  }

  public void "test expand to level"() {
    def text = """
class Foo {
    public static void main() {
        new Runnable(){
            public void run() {
            }
        }.run();
    }
}
"""
    configure text
    assertEquals 3, foldRegionsCount

    myFixture.editor.caretModel.moveToOffset(text.indexOf("new"))
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_TO_LEVEL_1)
    assertEquals 2, expandedFoldRegionsCount
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_TO_LEVEL_1)
    assertEquals 1, expandedFoldRegionsCount
  }
  
  public void "test expand recursively on expanded region containing collapsed regions"() {
    def text = """
class Foo {
    public static void main() {
        new Runnable(){
            public void run() {
            }
        }.run();
    }
}
"""
    configure text
    assertEquals 3, foldRegionsCount
    myFixture.editor.caretModel.moveToOffset(text.indexOf("run"))
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_REGION)
    myFixture.editor.caretModel.moveToOffset(text.indexOf("new"))
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_REGION)
    assertEquals 1, expandedFoldRegionsCount
    
    myFixture.editor.caretModel.moveToOffset(text.indexOf("main"))
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_REGION_RECURSIVELY)
    assertEquals 3, expandedFoldRegionsCount
  }

  public void "test single line closure unfolds when converted to multiline"() {
    boolean oldValue = Registry.is("editor.durable.folding.state")
    try {
      Registry.get("editor.durable.folding.state").setValue(false)

      def text = """
  class Foo {
    void m() {
      SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                  System.out.println();
              }
          });
    }
  }
  """
      configure text
      assert myFixture.editor.foldingModel.getCollapsedRegionAtOffset(text.indexOf("new Runnable"))
      myFixture.editor.caretModel.moveToOffset(text.indexOf("System"))
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.doHighlighting()
      assert myFixture.editor.foldingModel.getCollapsedRegionAtOffset(text.indexOf("new Runnable")) == null
    }
    finally {
      Registry.get("editor.durable.folding.state").setValue(oldValue)
    }
  }

  public void "test folding state is preserved for unchanged text in bulk mode"() {
    def text = """
class Foo {
    void m1() {

    }
    void m2() {

    }
}
"""
    configure text
    assertEquals 2, foldRegionsCount
    assertEquals 2, expandedFoldRegionsCount
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS)
    assertEquals 0, expandedFoldRegionsCount

    def document = (DocumentEx)myFixture.editor.document
    WriteCommandAction.runWriteCommandAction myFixture.project, {
      document.inBulkUpdate = true;
      try {
        document.insertString(document.getText().indexOf("}") + 1, "\n");
      }
      finally {
        document.inBulkUpdate = false;
      }
    }
    assertEquals 2, foldRegionsCount
    assertEquals 0, expandedFoldRegionsCount
  }

  public void "test processing of tabs inside fold regions"() {
    String text = """public class Foo {
\tpublic static void main(String[] args) {
\t\tjavax.swing.SwingUtilities.invokeLater(new Runnable() {
\t\t\t@Override
\t\t\tpublic void run() {
\t\t\t\tSystem.out.println();
\t\t\t}
\t\t});
\t}
}""";
    configure text
    assert myFixture.editor.getFoldingModel().getCollapsedRegionAtOffset(text.indexOf("new"))
    myFixture.editor.settings.useTabCharacter = true
    EditorTestUtil.configureSoftWraps(myFixture.editor, 1000)
    myFixture.editor.caretModel.moveToOffset(text.indexOf("System"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
    myFixture.checkResult("""public class Foo {
\tpublic static void main(String[] args) {
\t\tjavax.swing.SwingUtilities.invokeLater(new Runnable() {
\t\t\t@Override
\t\t\tpublic void run() {
\t\t\t\t\t<caret>System.out.println();
\t\t\t}
\t\t});
\t}
}""");
  }
  
  public void testCollapseExistingButExpandedBlock() {
    String text = '''class Foo {
    void m {
        if (true) {
            System.out.println();
        }
    }
}
'''
    configure text
    
    myFixture.editor.caretModel.moveToOffset(text.indexOf("System"))
    myFixture.performEditorAction("CollapseBlock")
    
    myFixture.performEditorAction("ExpandAllRegions")

    myFixture.editor.caretModel.moveToOffset(text.indexOf("System"))
    myFixture.performEditorAction("CollapseBlock")

    def topLevelRegions = ((FoldingModelEx)myFixture.editor.foldingModel).fetchTopLevel()
    assert topLevelRegions.length == 1
    assert topLevelRegions[0].startOffset == text.indexOf('{', text.indexOf("if"))
    assert topLevelRegions[0].endOffset == text.indexOf('}', text.indexOf("if")) + 1
  }
  
  public void "test editing near closure folding"() {
    configure """\
class Foo {
  void m() {
    SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                System.out.println();
            }
        });
  }
}
"""
    assertTopLevelFoldRegionsState "[FoldRegion +(56:143), placeholder='(Runnable) () → { ', FoldRegion +(164:188), placeholder=' }']"
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("SwingUtilities"))
    myFixture.type(' ')
    myFixture.doHighlighting()
    assertTopLevelFoldRegionsState "[FoldRegion +(57:144), placeholder='(Runnable) () → { ', FoldRegion +(165:189), placeholder=' }']"
  }
  
  public void "test folding update after external change"() {
    configure """\
class Foo {
  void m1() {
    System.out.println(1);
    System.out.println(2);
  }
  
  void m2() {
    System.out.println(3);
    System.out.println(4);
  }
}
"""
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS)
    assertTopLevelFoldRegionsState "[FoldRegion +(24:83), placeholder='{...}', FoldRegion +(99:158), placeholder='{...}']"
    
    def virtualFile = ((EditorEx)myFixture.getEditor()).virtualFile
    myFixture.saveText(virtualFile, """\
class Foo {
  void m1() {
    System.out.println(1);
    System.out.println(4);
  }
}
""")
    virtualFile.refresh(false, false)
    
    myFixture.doHighlighting()
    assertTopLevelFoldRegionsState "[FoldRegion +(24:83), placeholder='{...}']"
  }
  
  public void "test placeholder update on refactoring"() {
    configure """\
class Foo {
  void method() {}
  
  Foo foo = new Foo() {
    void method() {
      System.out.println();
    }
  };
}
"""
    assertTopLevelFoldRegionsState "[FoldRegion +(46:84), placeholder='method() → { ', FoldRegion +(105:115), placeholder=' }']"
    
    // emulate rename refactoring ('method' to 'otherMethod')
    def document = myFixture.editor.document
    WriteCommandAction.runWriteCommandAction myFixture.project, {
      int pos;
      while ((pos = document.getText().indexOf("method")) >= 0) {
        document.replaceString(pos, pos + "method".length(), "otherMethod")
      }
    }    

    myFixture.doHighlighting()
    assertTopLevelFoldRegionsState "[FoldRegion +(51:94), placeholder='otherMethod() → { ', FoldRegion +(115:125), placeholder=' }']"
  }

  private void assertTopLevelFoldRegionsState(String expectedState) {
    assertEquals(expectedState, myFixture.editor.foldingModel.toString())
  }

  private int getFoldRegionsCount() {
    return myFixture.editor.foldingModel.allFoldRegions.length
  }

  private int getExpandedFoldRegionsCount() {
    return myFixture.editor.foldingModel.allFoldRegions.count { it.isExpanded() ? 1 : 0}
  }
}
