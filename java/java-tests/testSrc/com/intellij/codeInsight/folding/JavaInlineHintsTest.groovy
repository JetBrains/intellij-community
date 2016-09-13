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
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

@SuppressWarnings("ALL")
// too many warnings in injections
public class JavaInlineHintsTest extends LightCodeInsightFixtureTestCase {

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

  private def configure(String text) {
    myFixture.configureByText("a.java", text)
    CodeFoldingManagerImpl.getInstance(getProject()).buildInitialFoldings(myFixture.editor);
    def foldingModel = myFixture.editor.foldingModel as FoldingModelEx
    foldingModel.rebuild()
    myFixture.doHighlighting()
  }
  
  public void "test insert boolean literal argument name"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    @Language("JAVA")
    def text = """class Groo {

 public void test(File file) {
  boolean testNow = System.currentTimeMillis() > 34000;
  int times = 1;
  float pi = 4;
  String title = "Testing...";
  char ch = 'q';

  configure(true, false, 555, 3.141f, "Huge Title", 'c', null);
  configure(testNow, shouldIgnoreRoots(), fourteen, pi, title, c, file);
 }

 public void configure(boolean testNow, boolean shouldIgnoreRoots, int times, float pii, String title, char terminate, File file) {
  System.out.println();
  System.out.println();
 }

}"""
    configure text
    PsiClass fooClass = JavaPsiFacade.getInstance(project).findClass('Groo', GlobalSearchScope.allScope(project))

    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 9

    checkTheFoldingStartsRightBefore(regions[1], text, "(testNow: ", "true")
    checkTheFoldingStartsRightBefore(regions[2], text, " shouldIgnoreRoots: ", "false")
    checkTheFoldingStartsRightBefore(regions[3], text, " times: ", "555")
    checkTheFoldingStartsRightBefore(regions[4], text, " pii: ", "3.141f")
    checkTheFoldingStartsRightBefore(regions[5], text, " title: ", '"Huge Title"')
    checkTheFoldingStartsRightBefore(regions[6], text, " terminate: ", "'c'")
    checkTheFoldingStartsRightBefore(regions[7], text, " file: ", "null")
  }

  public void "test do not inline name if setter"() {
    @Language("JAVA")
    def text = """class Groo {

 public void test() {
  setTestNow(false);
  System.out.println("");
 }

 public void setTestNow(boolean testNow) {
  System.out.println("");
  System.out.println("");
 }

}"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions
    assert regions.size() == 2
  }

  public void "test do not collapse varargs"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    @Language("JAVA")
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
    assert regions.size() == 4
    checkTheFoldingStartsRightBefore(regions[1], text, "(test: ", "13")
    checkTheFoldingStartsRightBefore(regions[2], text, " booleans...: ", "false")
  }

  public void "test do not inline varargs if no arguments"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    @Language("JAVA")
    def text = """
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    testBooleanVarargs(13);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 2
  }

  public void "test many varargs if no arguments"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    @Language("JAVA")
    def text = """
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    testBooleanVarargs(13, false, true, false);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }
    assert regions.size() == 4
    checkTheFoldingStartsRightBefore(regions[1], text, "(test: ", "13")
    checkTheFoldingStartsRightBefore(regions[2], text, " booleans...: ", "false")
  }
  
  public void "test do not inline if parameter length is one or two"() {
    @Language("JAVA")
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
    @Language("JAVA")
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
    @Language("JAVA")
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
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    @Language("JAVA")
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

    checkTheFoldingStartsRightBefore(regions[1], text, "(test: ", "100")

    checkTheFoldingStartsRightBefore(regions[2], text, " boo: ", "false")

    checkTheFoldingStartsRightBefore(regions[3], text, " seq: ", '"Hi!"')
  }

  public void "test inline negative and positive numbers"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);

    @Language("JAVA")
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

    checkTheFoldingStartsRightBefore(regions[1], text, "(test: ", "-1")
    checkTheFoldingStartsRightBefore(regions[2], text, "(test: ", "+1")
  }

  public void "test inline literal arguments with crazy settings"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    myFoldingSettings.setInlineLiteralParameterMinArgumentsToFold(1);
    myFoldingSettings.setInlineLiteralParameterMinNameLength(1);
    @Language("JAVA")
    def text = """
public class Test {
  public void main(boolean isActive, boolean requestFocus, int xoo) {
    System.out.println("AAA");
    main(true,false, /*comment*/2);
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }

    checkTheFoldingStartsRightBefore(regions[1], text, "(x: ", '"AAA"')
    checkTheFoldingStartsRightBefore(regions[2], text, "(isActive: ", "true")
    checkTheFoldingStartsRightBefore(regions[3], text, ",requestFocus: ", "false")
    checkTheFoldingStartsRightBefore(regions[4], text, "/xoo: ", "2")
  }

  public void "test inline literal arguments with generics"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    myFoldingSettings.setInlineLiteralParameterMinArgumentsToFold(1);
    myFoldingSettings.setInlineLiteralParameterMinNameLength(1);
    @Language("JAVA")
    def text = """
import java.util.*;
public class Test {
  public void main(Comparator<Integer> c, List<String> l) {
    c.compare(0, /** ddd */3);
    l.add(1, "uuu");
  }
}
"""
    configure text
    def regions = myFixture.editor.foldingModel.allFoldRegions.sort { it.startOffset }

    checkTheFoldingStartsRightBefore(regions[1], text, "(o1: ", '0')
    checkTheFoldingStartsRightBefore(regions[2], text, "/o2: ", "3")
    checkTheFoldingStartsRightBefore(regions[3], text, "(index: ", "1")
    checkTheFoldingStartsRightBefore(regions[4], text, " element: ", '"uuu"')
  }

  public void "test inline constructor literal arguments names"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    @Language("JAVA")
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

    checkTheFoldingStartsRightBefore(regions[1], text, "(isActive: ", "true")
    checkTheFoldingStartsRightBefore(regions[2], text, " requestFocus: ", "false")
  }

  private static def checkTheFoldingStartsRightBefore(FoldRegion region, String fileText, String placeHolder, String fileTextAfterFold) {
    assert region.endOffset == fileText.indexOf(fileTextAfterFold)
    assert region.startOffset == region.endOffset - 1
    assert region.placeholderText == placeHolder: "expected placeholder text: " + placeHolder + "; but got: " + region.placeholderText
  }

  public void "test inline anonymous class constructor literal arguments names"() {
    myFoldingSettings.setInlineParameterNamesForLiteralCallArguments(true);
    @Language("JAVA")
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

    checkTheFoldingStartsRightBefore(regions[2], text, "(counter: ", "10")
    checkTheFoldingStartsRightBefore(regions[3], text, " shouldTest: ", "false")
  }
}
