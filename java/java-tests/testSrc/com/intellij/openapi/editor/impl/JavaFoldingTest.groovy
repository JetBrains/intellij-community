// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.folding.CodeFoldingSettings
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilder
import com.intellij.find.FindManager
import com.intellij.java.codeInsight.folding.JavaFoldingTestCase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.DocumentUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NotNull

/**
 * @author Denis Zhdanov
 * @since 17.01.2011
 */
@SuppressWarnings("ALL") // too many warnings in injections
class JavaFoldingTest extends JavaFoldingTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7
  }

  public void testEndOfLineComments() { doTest() }

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
    @Language("JAVA")
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
    myFoldingSettings.setCollapseLambdas(true)
    @Language("JAVA")
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
    myFoldingSettings.setCollapseLambdas(true)
    @Language("JAVA")
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
    myFoldingSettings.setCollapseAccessors(true)
    @Language("JAVA")
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
    @Language("JAVA")
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
    @Language("JAVA")
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
    };
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
    @Language("JAVA")
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
    @Language("JAVA")
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

  public void testCustomFolding() { doTest() }
  public void testEmptyMethod() { doTest() }

  private doTest() {
    myFixture.testFolding("$PathManagerEx.testDataPath/codeInsight/folding/${getTestName(false)}.java")
  }

  public void "test custom folding IDEA-122715 and IDEA-87312"() {
    @Language("JAVA")
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
    @Language("JAVA")
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

  public void "test custom foldings have preference"() {
    @Language("JAVA")
    def text = """\
class A {
    // region Some
    @SuppressWarnings("")
    // endregion
    @Deprecated
    void m() {}
}
"""
    configure text
    assertTrue Arrays.toString(myFixture.editor.foldingModel.allFoldRegions).contains("Some")
  }

  public void "test custom foldings intersecting with comment foldings"() {
    @Language("JAVA")
    def text = """class Foo {
// 0
// 1
// region Some
// 2
// 3 next empty line is significant

// non-dangling
  int t = 1;
// 4
// 5
// endregion
// 6
// 7
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl

    assertFolding "// region"
    assertFolding "// 0"
    assertFolding "// 2" // Note: spans only two lines, see next test for details
    assertFolding "// 4"
    assertFolding "// 6"

    assertEquals 5, foldRegionsCount
  }

  public void "test single line comments foldings"() {
    @Language("JAVA")
    def text = """class Foo {
// 0
// 1
// 2 next empty line is significant

// 3 non-folded
// 4 non-folded
  int t = 1;
// 5
// 6
// 7
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl

    assertFolding "// 0"
    assertNoFoldingStartsAt "// 3"
    assertNoFoldingCovers "// 3"
    assertNoFoldingCovers "// 4"
    assertFolding "// 5"

    assertEquals 2, foldRegionsCount
  }

  public void "test custom folding collapsed by default"() {
    @Language("JAVA")
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
    @Language("JAVA")
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

  public void "test simple property accessors in one line"() {
    @Language("JAVA")
    def text = """class Foo {
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
    configure text
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
    @Language("JAVA")
    def text = """class Foo {
 @Override
 int someMethod() {
   return 0;
 }

 int someOtherMethod(
   int param) {
   return 0;
 }

}"""
    configure text
    PsiClass fooClass = JavaPsiFacade.getInstance(project).findClass('Foo', GlobalSearchScope.allScope(project))
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 3
    checkAccessorFolding(regions[0], regions[1], fooClass.methods[0])
  }

  public void "test don't inline array methods"() {
    @Language("JAVA")
    def text = """class Foo {
 int arrayMethod(int param)[] {
   return new int[0];
 }

}"""
    configure text
    assert myFixture.editor.foldingModel.allFoldRegions.size() == 1
  }

  public void "test don't inline very long one-line methods"() {
    @Language("JAVA")
    def text = """class Foo {
 int someVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable;

 // don't create folding that would exceed the right margin
 int getSomeVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable() {
   return someVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable;
 }
}"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 1
    assert regions[0].placeholderText == '{...}'
  }


  private def changeFoldRegions(Closure op) {
    myFixture.editor.foldingModel.runBatchFoldingOperationDoNotCollapseCaret(op)
  }

  public void "test unselect word should go inside folding group"() {
    @Language("JAVA")
    def text = """class Foo {
 int field;

 int getField() {
   return field;
 }

}"""
    configure text
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
    assert ' {\n   return field;\n }' == myFixture.editor.selectionModel.selectedText
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET)
    assert 'return field;' == myFixture.editor.selectionModel.selectedText
  }

  public void "test expand and collapse regions in selection"() {
    @Language("JAVA")
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
    @Language("JAVA")
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
    @Language("JAVA")
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
    @Language("JAVA")
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

  public void "test folding state is preserved for unchanged text in bulk mode"() {
    @Language("JAVA")
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
      DocumentUtil.executeInBulk(document, true, {
        document.insertString(document.getText().indexOf("}") + 1, "\n");
      } as Closure)
    }
    assertEquals 2, foldRegionsCount
    assertEquals 0, expandedFoldRegionsCount
  }

  public void "test processing of tabs inside fold regions"() {
    @Language("JAVA")
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
    @Language("JAVA")
    String text = '''class Foo {
    void m() {
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
    @Language("JAVA")
    def text = """\
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
    assertTopLevelFoldRegionsState "[FoldRegion +(56:143), placeholder='(Runnable) () → { ', FoldRegion +(164:188), placeholder=' }']"
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("SwingUtilities"))
    myFixture.type(' ')
    myFixture.doHighlighting()
    assertTopLevelFoldRegionsState "[FoldRegion +(57:144), placeholder='(Runnable) () → { ', FoldRegion +(165:189), placeholder=' }']"
  }

  public void "test folding update after external change"() {
    @Language("JAVA")
    def text = """\
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
    configure text
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS)
    assertTopLevelFoldRegionsState "[FoldRegion +(24:83), placeholder='{...}', FoldRegion +(97:156), placeholder='{...}']"

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
    @Language("JAVA")
    def text = """\
class Foo {
  void method() {}

  Foo foo = new Foo() {
    void method() {
      System.out.println();
    }
  };
}
"""
    configure text
    assertTopLevelFoldRegionsState "[FoldRegion +(44:82), placeholder='method() → { ', FoldRegion +(103:113), placeholder=' }']"

    // emulate rename refactoring ('method' to 'otherMethod')
    def document = myFixture.editor.document
    WriteCommandAction.runWriteCommandAction myFixture.project, {
      int pos;
      while ((pos = document.getText().indexOf("method")) >= 0) {
        document.replaceString(pos, pos + "method".length(), "otherMethod")
      }
    }

    myFixture.doHighlighting()
    assertTopLevelFoldRegionsState "[FoldRegion +(49:92), placeholder='otherMethod() → { ', FoldRegion +(113:123), placeholder=' }']"
  }

  public void "test imports remain collapsed when new item is added at the end"() {
    boolean oldValue = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true); // tests disable this by default
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true)
    try {
      configure """\
import java.util.ArrayList;
import java.util.List;

class Foo {
    public static void main(String[] args) {
        Class a = ArrayList.class;
        Class l = List.class;
        <caret>
    }
}
"""
      assertTopLevelFoldRegionsState "[FoldRegion +(7:50), placeholder='...']"

      myFixture.type("Class t = TreeMap.class;")
      myFixture.doHighlighting() // let auto-import complete
      myFixture.checkResult"""\
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

class Foo {
    public static void main(String[] args) {
        Class a = ArrayList.class;
        Class l = List.class;
        Class t = TreeMap.class;<caret>
    }
}
"""
      myFixture.doHighlighting() // update folding for the new text
      assertTopLevelFoldRegionsState "[FoldRegion +(7:76), placeholder='...']"
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = oldValue
    }
  }

  public void testGroupedFoldingsAreNotUpdatedOnUnrelatedDocumentChange() throws Exception {
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
    
    (myFixture.editor.foldingModel as FoldingModelEx).addListener(new FoldingListener() {
      @Override
      void onFoldRegionStateChange(@NotNull FoldRegion region) {
        fail "Unexpected fold region change"
      }

      @Override
      void onFoldProcessingEnd() {
        fail "Unexpected fold regions change"
      }
    }, myFixture.testRootDisposable)
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("SwingUtilities"))
    myFixture.type(' ')
    myFixture.doHighlighting()
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

  // Based on methods from GroovyFoldingTest
  private boolean assertFolding(int offset) {
    assert offset >= 0
    myFixture.editor.foldingModel.allFoldRegions.any { it.startOffset == offset }
  }

  private void assertFolding(String marker) {
    assert assertFolding(myFixture.file.text.indexOf(marker)), marker
  }

  private boolean assertNoFoldingCovers(int offset) {
    assert offset >= 0
    myFixture.editor.foldingModel.allFoldRegions.every { offset < it.startOffset || it.endOffset <= offset }
  }

  private void assertNoFoldingCovers(String marker) {
    assert assertNoFoldingCovers(myFixture.file.text.indexOf(marker)), marker
  }

  private boolean assertNoFoldingStartsAt(int offset) {
    assert offset >= 0
    myFixture.editor.foldingModel.allFoldRegions.every { offset != it.startOffset }
  }

  private void assertNoFoldingStartsAt(String marker) {
    assert assertNoFoldingStartsAt(myFixture.file.text.indexOf(marker)), marker
  }
}