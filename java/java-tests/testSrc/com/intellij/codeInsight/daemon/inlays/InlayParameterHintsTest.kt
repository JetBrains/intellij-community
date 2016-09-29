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
package com.intellij.codeInsight.daemon.inlays

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.DocumentUtil
import org.assertj.core.api.Assertions.assertThat


class InlayParameterHintsTest: LightCodeInsightFixtureTestCase() {

  private var isParamHintsEnabledBefore = false
  private var minParamLength = 3
  private var minArgsToShow = 2

  override fun setUp() {
    super.setUp()
    
    val settings = EditorSettingsExternalizable.getInstance()
    isParamHintsEnabledBefore = settings.isShowParameterNameHints
    minParamLength = settings.minParamNameLengthToShow
    minArgsToShow = settings.minArgsToShow
    
    settings.isShowParameterNameHints = true
  }

  override fun tearDown() {
    val settings = EditorSettingsExternalizable.getInstance()
    settings.isShowParameterNameHints = isParamHintsEnabledBefore
    settings.minParamNameLengthToShow = minParamLength
    settings.minArgsToShow = minArgsToShow
    
    super.tearDown()
  }

  fun `test insert literal arguments`() {
    setup("""
class Groo {

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

}""")

    onLineStartingWith("configure(true").assertInlays(
        "testNow->true",
        "shouldIgnoreRoots->false",
        "times->555",
        "pii->3.141f",
        """title->"Huge Title"""",
        "terminate->'c'",
        "file->null"
    )

    onLineStartingWith("configure(testNow").assertNoInlays()
  }

  fun `test do not show hints on setters`() {
    setup("""class Groo {

 public void test() {
  setTestNow(false);
  System.out.println("");
 }

 public void setTestNow(boolean testNow) {
  System.out.println("");
  System.out.println("");
 }

}""")
    
    onLineStartingWith("setTestNow").assertNoInlays()
  }
  
  fun `test single varargs hint`() {
    setup("""
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    testBooleanVarargs(13, false);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
""")
    
    onLineStartingWith("testBooleanVarargs")
        .assertInlays("test->13", "...booleans->false")
  }
  
  fun `test no hint if varargs null`() {
    setup("""
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
""")
    
    onLineStartingWith("testBooleanVarargs").assertInlays("test->13")
  }
  
  fun `test multiple vararg hint`() {
    setup("""
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
""")
    
    onLineStartingWith("testBooleanVarargs")
        .assertInlays("test->13", "...booleans->false")
  }
  
  fun `test do not inline if parameter length is one or two`() {
    setup("""
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
""")
    
    onLineStartingWith("count").assertInlays("t->1", "fa->false")
  }
  
  fun `test do not inline known subsequent parameter names`() {
    setup("""
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
""")
    
    onLineStartingWith("test1").assertNoInlays()
    onLineStartingWith("test2").assertNoInlays()
    onLineStartingWith("test3").assertNoInlays()
    onLineStartingWith("doTest").assertNoInlays()
  }
  
  fun `test show if can be assigned`() {
    setup("""
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
""")
    
    onLineStartingWith("count(1")
        .assertInlays("test->100", "boo->false", """seq->"Hi!"""")
  }
  
  fun `test inline positive and negative numbers`() {
    setup("""
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
""")
    
    onLineStartingWith("count(-")
        .assertInlays("test->-")
    
    onLineStartingWith("count(+")
        .assertInlays("test->+")
  }
  
  fun `test inline literal arguments with crazy settings`() {
    val settings = EditorSettingsExternalizable.getInstance()
    settings.minArgsToShow = 1
    settings.minParamNameLengthToShow = 1

    setup("""
public class Test {
  public void main(boolean isActive, boolean requestFocus, int xoo) {
    System.out.println("AAA");
    main(true,false, /*comment*/2);
  }
}
""")
    
    onLineStartingWith("System")
        .assertNoInlays()
    
    onLineStartingWith("main(t")
        .assertInlays("isActive->true", "requestFocus->false", "xoo->2")
  }
  
  
  fun `test ignored methods`() {
    setup("""
public class Test {
  
  public void main() {
    println("A");
    print("A");
    get(1);
    set(1, new Object());
    setNewIndex(10);
    "sss".contains("s");
    append("sdfsdf");
    "sfsdf".startWith("s");
    "sss".charAt(3);
    
    clearStatus(false);
  }
  
  void print(String s) {}
  void println(String s) {}
  void get(int index) {}
  void set(int index, Object object) {}
  void append(String s) {}
  void clearStatus(boolean updatedRecently) {}

}
""")

    val inlays = getInlays()
    assertThat(inlays).hasSize(1)
    
    assertThat(inlays[0].offset).isEqualTo(myFixture.editor.document.text.indexOf("false"))
  }
  
  fun `test hints for generic arguments`() {
    val settings = EditorSettingsExternalizable.getInstance()
    settings.minArgsToShow = 1
    settings.minParamNameLengthToShow = 1
    
    setup("""
import java.util.*;
public class Test {
  public void main(Comparator<Integer> c, List<String> l) {
    c.compare(0, /** ddd */3);
    l.add(1, "uuu");
  }
}
""")
    
    onLineStartingWith("c.compare")
        .assertInlays("o1->0", "o2->3")
    
    onLineStartingWith("l.add")
        .assertInlays("index->1", """element->"uuu"""")
  }
  
  fun `test inline constructor literal arguments names`() {
    setup("""
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
""")
    
    onLineStartingWith("Checker r")
        .assertInlays("isActive->true", "requestFocus->false")
  }
  
  fun `test inline anonymous class constructor parameters`() {
    setup("""
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
""")
    
    onLineStartingWith("Test t")
        .assertInlays("counter->10", "shouldTest->false")
  }

  fun `test inline if one of vararg params is literal`() {
    setup("""
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    int test = 13;
    boolean isCheck = false;
    boolean isOk = true;
    testBooleanVarargs(test, isCheck, true, isOk);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
""")
    
    onLineStartingWith("testBooleanVarargs")
        .assertInlays("...booleans->isCheck")
  }

  fun `test if any param matches inline all`() {
    setup("""
public class VarArgTest {
  
  public void main() {
    check(10, 1000);
  }

  public void check(int x, int paramNameLength) {
  }

}
""")

    onLineStartingWith("check(")
        .assertInlays("x->10", "paramNameLength->1000")
  }

  fun `test inline common name pair if more that 2 args`() {
    setup("""
public class VarArgTest {
  
  public void main() {
    String s = "su";
    check(10, 1000, s);
  }

  public void check(int beginIndex, int endIndex, String params) {
  }

}
""")

    onLineStartingWith("check(")
        .assertInlays("beginIndex->10", "endIndex->1000")
  }

  fun `test inline common name pair if more that 2 args xxx`() {
    setup("""
public class VarArgTest {
  
  public void main() {
    check(10, 1000, "su");
  }

  public void check(int beginIndex, int endIndex, String x) {
  }

}
""")

    onLineStartingWith("check(")
        .assertInlays("beginIndex->10", "endIndex->1000", """x->"su"""" )
  }
  
  fun `test inline this`() {
    setup("""
public class VarArgTest {
  
  public void main() {
    check(this, 1000);
  }

  public void check(VarArgTest test, int endIndex) {
  }

}
""")
    
    onLineStartingWith("check(t")
        .assertInlays("test->this", "endIndex->1000")
  }

  fun `test do not show single parameter hint if it is string literal`() {
    setup("""
public class Test {
  
  public void test() {
    debug("Error message");
    info("Error message", new Object());
  }

  void debug(String message) {}
  void info(String message, Object error) {}
  
}
""")

    onLineStartingWith("debug(").assertNoInlays()
    onLineStartingWith("info(").assertNoInlays()
  }

  fun `test show hints for literals if there are many of them`() {
    setup("""
public class Test {
  
  public void test() {
    int a = 2;
    debug("Debug", "DTitle", a);
    info("Error message", "Title");
  }

  void debug(String message, String title, int value) {}
  void info(String message, String title) {}
  
}
""")

    onLineStartingWith("debug(").assertInlays("message->\"Debug\"", "title->\"DTitle\"")
    onLineStartingWith("info(").assertInlays("message->\"Error message\"", "title->\"Title\"")
  }
  
  private fun getInlays(): List<Inlay> {
    val editor = myFixture.editor
    return editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
  }

  private fun onLineStartingWith(text: String): InlayAssert {
    val range = getLineRangeStartingWith(text)
    val inlays = myFixture.editor.inlayModel.getInlineElementsInRange(range.startOffset, range.endOffset)
    return InlayAssert(myFixture.file, inlays)
  }
  
  private fun getLineRangeStartingWith(text: String): TextRange {
    val document = myFixture.editor.document
    val startOffset = document.charsSequence.indexOf(text)
    val lineNumber = document.getLineNumber(startOffset)
    return DocumentUtil.getLineTextRange(document, lineNumber)
  }

  private fun setup(text: String) {
    myFixture.configureByText("a.java", text)
    myFixture.doHighlighting()
  }
  
}

private class InlayAssert(private val file: PsiFile, private val inlays: List<Inlay>) {

  fun assertNoInlays() {
    assertThat(inlays).hasSize(0)
  }
  
  fun assertInlays(vararg expectedInlays: String) {
    assertThat(expectedInlays.size).isNotEqualTo(0)
    
    val hintManager = ParameterHintsPresentationManager.getInstance()
    val hints = inlays.filter { hintManager.isParameterHint(it) }.map { it.offset to hintManager.getHintText(it) }
    val hintOffsets = hints.map { it.first }
    val hintNames = hints.map { it.second }

    assertThat(hints.size).isEqualTo(expectedInlays.size)

    val expect = expectedInlays.map { it.substringBefore("->") to it.substringAfter("->") }
    val expectedHintNames = expect.map { it.first }
    val expectedWordsAfter = expect.map { it.second }

    assertThat(hintNames).isEqualTo(expectedHintNames)

    val wordsAfter = hintOffsets.mapNotNull { file.findElementAt(it) }.map { it.text }

    assertThat(wordsAfter).isEqualTo(expectedWordsAfter)
  }
  
}
