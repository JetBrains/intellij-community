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

import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import org.assertj.core.api.Assertions

class JavaInlayParameterHintsTest : InlayParameterHintsTest() {

  fun setup(text: String) = configureFile("a.java", text)

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


  fun `test do not show for Exceptions`() {
    setup("""
class Fooo {
  
  public void test() {
    Throwable t = new IllegalStateException("crime");
  }
  
}
""")

    onLineStartingWith("Throw").assertNoInlays()
  }


  fun `test show hint for single string literal if there is multiple string params`() {
    setup("""class Groo {

 public void test() {
   String message = "sdfsdfdsf";
   assertEquals("fooo", message);

   String title = "TT";
   show(title, "Hi");
 }

 public void assertEquals(String expected, String actual) {}
 public void show(String title, String message) {}

}""")

    onLineStartingWith("assertEquals").assertInlays("expected->\"fooo\"")
    onLineStartingWith("show").assertInlays("message->\"Hi\"")
  }

  fun `test no hints for generic builders`() {
    setup("""
class Foo {
  void test() {
    new IntStream().skip(10);
    new Stream<Integer>().skip(10);
  }
}

class IntStream {
  public IntStream skip(int n) {}
}

class Stream<T> {
  public Stream<T> skip(int n) {}
}
""")

    onLineStartingWith("new IntStream").assertNoInlays()
    onLineStartingWith("new Stream").assertNoInlays()
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
  
  List<String> list = new ArrayList<>();
  StringBuilder builder = new StringBuilder();

  public void main() {
    System.out.println("A");
    System.out.print("A");
    
    list.add("sss");
    list.get(1);
    list.set(1, "sss");
    
    setNewIndex(10);
    "sss".contains("s");
    builder.append("sdfsdf");
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
    Assertions.assertThat(inlays).hasSize(1)

    Assertions.assertThat(inlays[0].offset).isEqualTo(myFixture.editor.document.text.indexOf("false"))
  }

  fun `test hints for generic arguments`() {
    val settings = EditorSettingsExternalizable.getInstance()
    settings.minArgsToShow = 1
    settings.minParamNameLengthToShow = 1

    setup("""

class QList<E> {
  void add(int query, E obj) {}
}

class QCmp<E> {
  void compare(E o1, E o2) {}
}


public class Test {
  public void main(QCmp<Integer> c, QList<String> l) {
    c.compare(0, /** ddd */3);
    l.add(1, "uuu");
  }
}
""")

    onLineStartingWith("c.compare")
      .assertInlays("o1->0", "o2->3")

    onLineStartingWith("l.add")
      .assertInlays("query->1", """obj->"uuu"""")
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

  fun `test ignore String methods`() {
    setup("""
class Test {
  
  public void main() {
    String.format("line", "eee", "www");
  }
  
}
""")

    onLineStartingWith("String").assertNoInlays()
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
      .assertInlays("beginIndex->10", "endIndex->1000", """x->"su"""")
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

  fun `test inline strange methods`() {
    setup("""
public class Test {
  
  void main() {
    createContent(null);
    createNewContent(this);
  }

  Content createContent(DockManager manager) {}
  Content createNewContent(Test test) {}

}
interface DockManager {}
interface Content {}
""")

    onLineStartingWith("createContent").assertInlays("manager->null")
    onLineStartingWith("createNewContent").assertInlays("test->this")
  }

  fun `test do not inline builder pattern`() {
    setup("""
class Builder {
  void await(boolean value) {}
  Builder bwait(boolean xvalue) {}
  Builder timeWait(int time) {}
}

class Test {
  
  public void test() {
    Builder builder = new Builder();
    builder.await(true);
    builder.bwait(false).timeWait(100);
  }
  
}
""")

    onLineStartingWith("builder.await").assertInlays("value->true")
    onLineStartingWith("builder.bwait").assertNoInlays()
  }

  fun `test builder method only method with one param`() {
    setup("""
class Builder {
  Builder qwit(boolean value, String sValue) {}
  Builder trew(boolean value) {}
}

class Test {
  public void test() {
    Builder builder = new Builder();
    builder
    .trew(false)
    .qwit(true, "value");
  }
}
""")

    onLineStartingWith(".trew").assertNoInlays()
    onLineStartingWith(".qw").assertInlays("value->true", "sValue->\"value\"")
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

  fun `test show single`() {
    setup("""
class Test {

  void main() {
    blah(1, 2);
    int z = 2;
    draw(10, 20, z);
    int x = 10;
    int y = 12;
    drawRect(x, y, 10, 12);
  }

  void blah(int a, int b) {}
  void draw(int x, int y, int z) {}
  void drawRect(int x, int y, int w, int h) {}

}
""")

    onLineStartingWith("blah").assertInlays("a->1", "b->2")
    onLineStartingWith("draw").assertInlays("x->10", "y->20")
    onLineStartingWith("drawRect").assertInlays("w->10", "h->12")
  }

  fun `test do not show for setters`() {
    setup("""
class Test {
  
  void main() {
    set(10);
  }
  
  void set(int newValue) {}
  
}
""")

    onLineStartingWith("set(").assertNoInlays()
  }

  fun `test show for method with boolean param and return value`() {
    setup("""
class Test {
  
  void test() {
    String name = getTestName(true);
    System.out.println("");
    "xxx".equals(name);
    Math.min(10, 20);
  }
  
  String getTestName(boolean lowerCase) {}
}
""")

    onLineStartingWith("String name").assertInlays("lowerCase->true")
    onLineStartingWith("\"xxx\"").assertNoInlays()
    onLineStartingWith("Math").assertNoInlays()
  }

  fun `test more blacklisted items`() {
    setup("""
class Test {
  
  void test() {
    System.getProperty("aaa");
    System.setProperty("aaa", "bbb");
    new Key().create(10);
  }
  
}

class Key {
  void create(int a) {}
}
""")

    onLineStartingWith("System.get").assertNoInlays()
    onLineStartingWith("System.set").assertNoInlays()
    onLineStartingWith("new").assertNoInlays()
  }

  fun `test poly and binary expressions`() {
    setup("""
class Test {
  void test() {
    xxx(100);
    check(1 + 1);
    int i=1; check(1 + 1 + 1);
    yyy(200);
  }
  void check(int isShow) {}
  void xxx(int followTheSum) {}
  void yyy(int followTheSum) {}
}
""")

    onLineStartingWith("xxx").assertInlays("followTheSum->100")
    onLineStartingWith("check").assertInlays("isShow->1")
    onLineStartingWith("int").assertInlays("isShow->1")
    onLineStartingWith("yyy").assertInlays("followTheSum->200")
  }

  fun `test incorrect pattern`() {
    ParameterNameHintsSettings.getInstance().addIgnorePattern(JavaLanguage.INSTANCE, "")
    setup("""
class Test {
  void test() {
    check(1000);  
  }
  void check(int isShow) {}
}
""")
  }

  fun `test do not show hint for name contained in method`() {
    ParameterNameHintsSettings.getInstance().isShowParamNameContainedInMethodName = false
    setup("""
class Test {
  void main() {
    timeoutExecution(1000);
    createSpace(true);
  }
  void timeoutExecution(int timeout) {}
  void createSpace(boolean space) {}
}
""")

    onLineStartingWith("timeoutExec").assertNoInlays()
    onLineStartingWith("createSpace").assertNoInlays()
  }

  fun `test show if multiple params but name contained`() {
    ParameterNameHintsSettings.getInstance().isShowParamNameContainedInMethodName = false
    setup("""
class Test {
  void main() {
    timeoutExecution(1000, "xxx");
    createSpace(true, 10);
  }
  void timeoutExecution(int timeout, String message) {}
  void createSpace(boolean space, int a) {}
}
""")

    onLineStartingWith("timeout")
      .assertInlays("timeout->1000", "message->\"xxx\"")

    onLineStartingWith("createSpace")
      .assertInlays("space->true", "a->10")
  }

  fun `test show same params`() {
    ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType = true
    setup("""
class Test {
  void main() {
    String c = "c";
    String d = "d";
    test(c, d);
  }
  void test(String parent, String child) {
  }
}
""")

    onLineStartingWith("test").assertInlays("parent->c", "child->d")
  }

  fun `test show triple`() {
    ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType = true
    setup("""
class Test {
  void main() {
    String c = "c";
    test(c, c, c);
  }
  void test(String parent, String child, String grandParent) {
  }
}
""")
    onLineStartingWith("test").assertInlays("parent->c", "child->c", "grandParent->c")
  }

  fun `test show couple of doubles`() {
    ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType = true
    setup("""
class Test {
  void main() {
    String c = "c";
    String d = "d";
    int v = 10;
    test(c, d, v, v);
  }
  void test(String parent, String child, int vx, int vy) {
  }
}
""")
    onLineStartingWith("test").assertInlays("parent->c", "child->d", "vx->v", "vy->v")
  }

}