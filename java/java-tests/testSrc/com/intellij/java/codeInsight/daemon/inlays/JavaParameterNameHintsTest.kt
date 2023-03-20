// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.JavaInlayParameterHintsProvider
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Inlay
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat

class JavaInlayParameterHintsTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_8_ANNOTATED
  }

  override fun tearDown() {
    try {
      val default = ParameterNameHintsSettings()
      ParameterNameHintsSettings.getInstance().loadState(default.state)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun check(text: String) {
    myFixture.configureByText("A.java", text)
    myFixture.testInlays()
  }

  fun `test insert literal arguments`() {
    check("""
class Groo {

 public void test(File file) {
  boolean testNow = System.currentTimeMillis() > 34000;
  int times = 1;
  float pi = 4;
  String title = "Testing...";
  char ch = 'q';

  configure(<hint text="testNow:"/>true, <hint text="times:"/>555, <hint text="pii:"/>3.141f, <hint text="title:"/>"Huge Title", <hint text="terminate:"/>'c', <hint text="file:"/>null);
  configure(testNow, shouldIgnoreRoots(), fourteen, pi, title, c, file);
 }

 public void configure(boolean testNow, int times, float pii, String title, char terminate, File file) {
  System.out.println();
  System.out.println();
 }

}""")
  }

  fun `test do not show for Exceptions`() {
    check("""
class Fooo {

  public void test() {
    Throwable t = new IllegalStateException("crime");
  }

}
""")
  }


  fun `test show hint for single string literal if there is multiple string params`() {
    check("""class Groo {

 public void test() {
   String message = "sdfsdfdsf";
   assertEquals(<hint text="expected:"/>"fooo", message);

   String title = "TT";
   show(title, <hint text="message:"/>"Hi");
 }

 public void assertEquals(String expected, String actual) {}
 public void show(String title, String message) {}

}""")
  }


  fun `test do not show hints on setters`() {
    check("""class Groo {

 public void test() {
  setTestNow(false);
  System.out.println("");
 }

 public void setTestNow(boolean testNow) {
  System.out.println("");
  System.out.println("");
 }

}""")
  }


  fun `test single varargs hint`() {
    check("""
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    testBooleanVarargs(<hint text="test:"/>13, <hint text="...booleans:"/>false);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
""")
  }

  fun `test no hint if varargs null`() {
    check("""
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    testBooleanVarargs(<hint text="test:"/>13);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
""")
  }


  fun `test multiple vararg hint`() {
    check("""
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    testBooleanVarargs(<hint text="test:"/>13, <hint text="...booleans:"/>false, true, false);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
""")
  }


  fun `test do not inline known subsequent parameter names`() {
    check("""
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
  }


  fun `test show if can be assigned`() {
    check("""
public class CharSymbol {

  public void main() {
    Object obj = new Object();
    count(<hint text="test:"/>100, <hint text="boo:"/>false, <hint text="seq:"/>"Hi!");
  }

  public void count(Integer test, Boolean boo, CharSequence seq) {
    int a = test;
    Object obj = new Object();
  }
}
""")
  }

  fun `test inline positive and negative numbers`() {
    check("""
public class CharSymbol {

  public void main() {
    Object obj = new Object();
    count(<hint text="test:"/>-1, obj);
    count(<hint text="test:"/>+1, obj);
  }

  public void count(int test, Object obj) {
    Object tmp = obj;
    boolean isFast = false;
  }
}
""")
  }

  fun `test inline literal arguments with crazy settings`() {
    check("""
public class Test {
  public void main(boolean isActive, boolean requestFocus, int xoo) {
    System.out.println("AAA");
    main(<hint text="isActive:"/>true,<hint text="requestFocus:"/>false, <hint text="xoo:"/>2);
  }
}
""")

  }

  fun `test suppress for erroneous parameters`() {
    JavaInlayParameterHintsProvider.getInstance().showForParamsWithSameType.set(false)
    check("""
public class Test {
    void foo(String foo) {}
    void foo(String foo, String bar) {}
    void foo(String foo, String bar, String baz) {}

    void test() {
        foo("a"++"b"); // no hint
    }
}
""")
    JavaInlayParameterHintsProvider.getInstance().showForParamsWithSameType.set(true)
    check("""
public class Test {
    void foo(String foo) {}
    void foo(String foo, String bar) {}
    void foo(String foo, String bar, String baz) {}

    void test() {
        foo(<hint text="foo:"/>"a"++"b");
    }
}
""")

  }


  fun `test ignored methods`() {
    check("""
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

    clearStatus(<hint text="updatedRecently:"/>false);
  }

  void print(String s) {}
  void println(String s) {}
  void get(int index) {}
  void set(int index, Object object) {}
  void append(String s) {}
  void clearStatus(boolean updatedRecently) {}

}
""")

  }

  fun `test hints for generic arguments`() {
    check("""

class QList<E> {
  void add(int query, E obj) {}
}

class QCmp<E> {
  void cmpre(E oe1, E oq2) {}
}


public class Test {
  public void main(QCmp<Integer> c, QList<String> l) {
    c.cmpre(<hint text="oe1:"/>0, <hint text="oq2:"/>3);
    l.add(<hint text="query:"/>1, <hint text="obj:"/>"uuu");
  }
}
""")
  }

  fun `test inline constructor literal arguments names`() {
    check("""
public class Test {

  public void main() {
    System.out.println("AAA");
    Checker r = new Checker(<hint text="isActive:"/>true, <hint text="requestFocus:"/>false) {
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
  }

  fun `test inline anonymous class constructor parameters`() {
    check("""
public class Test {

  Test(int counter, boolean shouldTest) {
    System.out.println();
    System.out.println();
  }

  public static void main() {
    System.out.println();
    Test t = new Test(<hint text="counter:"/>10, <hint text="shouldTest:"/>false);
  }

}
""")
  }

  fun `test inline if one of vararg params is literal`() {
    check("""
public class VarArgTest {

  public void main() {
    System.out.println("AAA");
    int test = 13;
    boolean isCheck = false;
    boolean isOk = true;
    testBooleanVarargs(test, <hint text="...booleans:"/>isCheck, true, isOk);
  }

  public boolean testBooleanVarargs(int test, Boolean... booleans) {
    int temp = test;
    return false;
  }
}
""")
  }

  fun `test if any param matches inline all`() {
    check("""
public class VarArgTest {

  public void main() {
    check(<hint text="x:"/>10, <hint text="paramNameLength:"/>1000);
  }

  public void check(int x, int paramNameLength) {
  }

}
""")
  }

  fun `test inline common name pair if more that 2 args`() {
    check("""
public class VarArgTest {

  public void main() {
    String s = "su";
    check(<hint text="beginIndex:"/>10, <hint text="endIndex:"/>1000, s);
  }

  public void check(int beginIndex, int endIndex, String params) {
  }

}
""")
  }

  fun `test ignore String methods`() {
    check("""
class Test {

  public void main() {
    String.format(<hint text="s:"/>"line", <hint text="...objects:"/>"eee", "www");
  }

}
""")
  }

  fun `test inline common name pair if more that 2 args xxx`() {
    check("""
public class VarArgTest {

  public void main() {
    check(<hint text="beginIndex:"/>10, <hint text="endIndex:"/>1000, <hint text="x:"/>"su");
  }

  public void check(int beginIndex, int endIndex, String x) {
  }

}
""")
  }

  fun `test inline this`() {
    check("""
public class VarArgTest {

  public void main() {
    check(<hint text="test:"/>this, <hint text="endIndex:"/>1000);
  }

  public void check(VarArgTest test, int endIndex) {
  }

}
""")
  }

  fun `test inline strange methods`() {
    check("""
public class Test {

  void main() {
    createContent(<hint text="manager:"/>null);
    createNewContent(<hint text="test:"/>this);
  }

  Content createContent(DockManager manager) {}
  Content createNewContent(Test test) {}

}
interface DockManager {}
interface Content {}
""")
  }

  fun `test do not show single parameter hint if it is string literal`() {
    check("""
public class Test {

  public void test() {
    debug("Error message");
    info("Error message", new Object());
  }

  void debug(String message) {}
  void info(String message, Object error) {}

}
""")
  }

  fun `test show single`() {
    check("""
class Test {

  void main() {
    blah(<hint text="a:"/>1, <hint text="b:"/>2);
    int z = 2;
    draw(<hint text="x:"/>10, <hint text="y:"/>20, z);
    int x = 10;
    int y = 12;
    drawRect(x, y, <hint text="w:"/>10, <hint text="h:"/>12);
  }

  void blah(int a, int b) {}
  void draw(int x, int y, int z) {}
  void drawRect(int x, int y, int w, int h) {}

}
""")
  }

  fun `test do not show for setters`() {
    check("""
class Test {

  void main() {
    set(10);
    setWindow(100);
    setWindow(<hint text="height:"/>100, <hint text="weight:">);
  }

  void set(int newValue) {}
  void setWindow(int newValue) {}
  void setWindow(int height, int weight) {}

}
""")
  }

  fun `test do not show for equals and min`() {
    check("""
class Test {
  void test() {
    "xxx".equals(name);
    Math.min(10, 20);
  }
}
""")
  }

  fun `test more blacklisted items`() {
    check("""
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
  }

  fun `test poly and binary expressions`() {
      check("""
class Test {
  void test() {
    xxx(<hint text="followTheSum:"/>100);
    check(<hint text="isShow:"/>1 + 1);
    check(<hint text="isShow:"/>1 + 1 + 1);
    yyy(<hint text="followTheSum:"/>200);
  }
  void check(int isShow) {}
  void xxx(int followTheSum) {}
  void yyy(int followTheSum) {}
}
""")
  }

  fun `test incorrect pattern`() {
    ParameterNameHintsSettings.getInstance().addIgnorePattern(JavaLanguage.INSTANCE, "")
    check("""
class Test {
  void test() {
    check(<hint text="isShow:"/>1000);
  }
  void check(int isShow) {}
}
""")
  }

  fun `test do not show hint for name contained in method`() {
    JavaInlayParameterHintsProvider.getInstance().showIfMethodNameContainsParameterName.set(false)
    check("""
class Test {
  void main() {
    timeoutExecution(1000);
    createSpace(true);
  }
  void timeoutExecution(int timeout) {}
  void createSpace(boolean space) {}
}
""")
  }

  fun `test show if multiple params but name contained`() {
    JavaInlayParameterHintsProvider.getInstance().showIfMethodNameContainsParameterName.set(false)
    check("""
class Test {
  void main() {
    timeoutExecution(<hint text="timeout:"/>1000, <hint text="message:"/>"xxx");
    createSpace(<hint text="space:"/>true, <hint text="a:"/>10);
  }
  void timeoutExecution(int timeout, String message) {}
  void createSpace(boolean space, int a) {}
}
""")
  }

  fun `test show same params`() {
    JavaInlayParameterHintsProvider.getInstance().showForParamsWithSameType.set(true)
    check("""
class Test {
  void main() {
    String c = "c";
    String d = "d";
    test(<hint text="parent:"/>c, <hint text="child:"/>d);
  }
  void test(String parent, String child) {
  }
}
""")
  }

  fun `test show triple`() {
    JavaInlayParameterHintsProvider.getInstance().showForParamsWithSameType.set(true)
    check("""
class Test {
  void main() {
    String c = "c";
    test(<hint text="parent:"/>c, <hint text="child:"/>c, <hint text="grandParent:"/>c);
  }
  void test(String parent, String child, String grandParent) {
  }
}
""")
  }

  fun `test show couple of doubles`() {
    JavaInlayParameterHintsProvider.getInstance().showForParamsWithSameType.set(true)
    check("""
class Test {
  void main() {
    String c = "c";
    String d = "d";
    int v = 10;
    test(<hint text="parent:"/>c, <hint text="child:"/>d, <hint text="vx:"/>v, <hint text="vy:"/>v);
  }
  void test(String parent, String child, int vx, int vy) {
  }
}
""")
  }

  fun `test show ambigous`() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
class Test {
  void main() {
    test(10<selection>, 100</selection>);
  }
  void test(int a, String bS) {}
  void test(int a, int bI) {}
}
""")

    myFixture.doHighlighting()
    val hints = getHints()
    assertThat(hints.size).isEqualTo(2)
    assertThat(hints[0]).isEqualTo("a:")
    assertThat(hints[1]).isEqualTo("bI:")

    myFixture.type('\b')

    myFixture.doHighlighting()
    assertSingleInlayWithText("a:")
  }

  fun `test show ambiguous constructor`() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
class Test {
  void main() {
    new X(10<selection>, 100</selection>);
  }
}

class X {
  X(int a, int bI) {}
  X(int a, String bS) {}
}
""")

    myFixture.doHighlighting()
    val hints = getHints()
    assertThat(hints.size).isEqualTo(2)
    assertThat(hints[0]).isEqualTo("a:")
    assertThat(hints[1]).isEqualTo("bI:")

    myFixture.type('\b')
    myFixture.doHighlighting()

    assertSingleInlayWithText("a:")
  }

  fun `test preserved inlays`() {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
class Test {
  void main() {
    test(<caret>);
  }

  void test(int fooo) {}
}
""")
    
    myFixture.type("100")
    myFixture.doHighlighting()
    assertSingleInlayWithText("fooo:")
    
    myFixture.type("\b\b\b")
    myFixture.doHighlighting()
    assertSingleInlayWithText("fooo:")
    
    myFixture.type("yyy")
    myFixture.doHighlighting()
    assertSingleInlayWithText("fooo:")
    
    myFixture.checkResult(
"""
class Test {
  void main() {
    test(yyy);
  }

  void test(int fooo) {}
}
""")
  }

  fun `test do not show hints if method is unknown and one of them or both are blacklisted`() {
    ParameterNameHintsSettings.getInstance().addIgnorePattern(JavaLanguage.INSTANCE, "*kee")
    myFixture.configureByText(JavaFileType.INSTANCE, """
class Test {
  void main() {
    kee(100<caret>)
  }

  void kee(int a) {}
  void kee(String a) {}
}
""")

    myFixture.doHighlighting()
    var hints = getHints()
    assertThat(hints).hasSize(0)

    myFixture.type('+')
    myFixture.doHighlighting()

    hints = getHints()
    assertThat(hints).hasSize(0)
  }

  
  fun `test multiple hints on same offset lives without exception`() {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
class Test {
    void main() {
        check(<selection>0, </selection>200);
    }

    void check(int qas, int b) {
    }
}
""")

    myFixture.doHighlighting()

    var inlays = getHints()
    assert(inlays.size == 2)
    
    myFixture.type('\b')
    myFixture.doHighlighting()
    
    inlays = getHints()
    assert(inlays.size == 1 && inlays.first() == "qas:") { "Real inlays ${inlays.size}" }
  }


  fun `test params with same type`() {
    JavaInlayParameterHintsProvider.getInstance().showForParamsWithSameType.set(true)
    check("""
class Test {
  void test() {
    String parent, child, element;
    check(<hint text="a:"/>10, parent, child);
    check(<hint text="a:"/>10, <hint text="parent:">element, child);
  }
  void check(int a, String parent, String child) {}
}
""")
  }

  fun `test if resolved but no hints just return no hints`() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
public class Test {

    public void main() {
        foo(1<caret>);
    }

    void foo(int a) {}
    void foo() {}
}
""")

    myFixture.doHighlighting()

    var inlays = getHints()
    assert(inlays.size == 1)


    myFixture.type('\b')

    myFixture.performEditorAction("EditorLeft")
    myFixture.doHighlighting()

    inlays = getHints()
    assert(inlays.isEmpty())
  }

  fun `test one-char one-digit hints enabled`() {
    JavaInlayParameterHintsProvider.getInstance().ignoreOneCharOneDigitHints.set(true)
    check("""
class Test {
  void main() {
    timeoutExecution(<hint text="t1:"/>1, <hint text="t2:"/>2, <hint text="t3:"/>3, <hint text="t4:"/>4, <hint text="t5:"/>5, <hint text="t6:"/>6, <hint text="t7:"/>7, <hint text="t8:"/>8, <hint text="t9:"/>9, <hint text="t10:"/>10);
  }
  void timeoutExecution(int t1, int t2, int t3, int t4, int t5, int t6, int t7, int t8, int t9, int t10) {}
}
""")
  }

  fun `test ordered sequential`() {
    JavaInlayParameterHintsProvider.getInstance().ignoreOneCharOneDigitHints.set(false)
    check("""
class Test {
  void main() {
    rect(<hint text="x1:"/>1, <hint text="y1:"/>2, <hint text="x2:"/>3, <hint text="y2:"/>4);
    fromZero(1, 2);
    fromOne(1, 2);
    fromX(<hint text="x86:"/>1, <hint text="x87:"/>2);
    fromX(<hint text="x86:"/>1);
  }
  void rect(int x1, int y1, int x2, int y2) {}
  void fromZero(int x0, int x1) {}
  void fromOne(int x1, int x2) {}
  void fromX(int x86, int x87) {}
  void fromX(int x86) {}
}
""")
  }

  fun `test unordered sequential`() {
    JavaInlayParameterHintsProvider.getInstance().ignoreOneCharOneDigitHints.set(false)
    check("""
class Test {
  void test() {
    unordered(<hint text="x1:"/>100, <hint text="x3:"/>200);
    unordered2(<hint text="x0:"/>100, <hint text="x3:"/>200);
  }
  void unordered(int x1, int x3) {}
  void unordered2(int x0, int x3) {}
}
""")
  }

  fun `test ordered with varargs`() {
    JavaInlayParameterHintsProvider.getInstance().ignoreOneCharOneDigitHints.set(false)
    check("""
class Test {
  void test() {
    ord(100, 200);
    ord2(100, 200);
  }
  void ord(int x1, int x2, int... others) {}
  void ord2(int x0, int x1, int... others) {}
}
""")
  }

  fun `test one-char one-digit hints disabled`() {
    JavaInlayParameterHintsProvider.getInstance().ignoreOneCharOneDigitHints.set(false)
    check("""
class Test {
  void main() {
    timeoutExecution(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }
  void timeoutExecution(int t1, int t2, int t3, int t4, int t5, int t6, int t7, int t8, int t9, int t10) {}
}
""")
  }

  fun `test just some unparsable parameter name`() {
    JavaInlayParameterHintsProvider.getInstance().ignoreOneCharOneDigitHints.set(false)
    check("""
class Test {
  void main() {
    check(<hint text="main2toMain:"/>100);
    www(<hint text="x86:"/>100);
  }
  void check(int main2toMain) {}
  void www(int x86) {}
}
""")
  }

  fun `test unclear expression type setting true`() {
    JavaInlayParameterHintsProvider.getInstance().isShowHintWhenExpressionTypeIsClear.set(true)
    check("""
class Test {
  void main() {
        String data = "asdad";
        foo(<hint text="info:"/>data);
  }

  void foo(String info) {}
}
""")
  }


  fun `test unclear expression type setting false`() {
    JavaInlayParameterHintsProvider.getInstance().isShowHintWhenExpressionTypeIsClear.set(false)
    check("""
class Test {
  void main() {
        String data = "asdad";
        foo(data);
  }

  void foo(String info) {}
}
""")
  }


  fun `test enum parameter names`() {
    check("""
public enum Thingy {
    ONE(<hint text="green:"/>false, <hint text="striped:"/>true),
    TWO(<hint text="green:"/>false, <hint text="striped:"/>false),
    THREE(<hint text="...x:"/>12,32,3,2,32,3,2,3,23);
    private boolean green;
    private boolean striped;

    Thingy(final boolean green, final boolean striped) {
        this.green = green;
        this.striped = striped;
    }

    Thingy(int... x) {
    }
}""")
  }


  fun `test enum parameter names disabled`() {
    JavaInlayParameterHintsProvider.getInstance().isShowHintsForEnumConstants.set(false)
    check("""
public enum Thingy {
    ONE(false, true),
    TWO(false, false),
    THREE(12,32,3,2,32,3,2,3,23);
    private boolean green;
    private boolean striped;

    Thingy(final boolean green, final boolean striped) {
        this.green = green;
        this.striped = striped;
    }

    Thingy(int... x) {
    }
}""")
  }


  fun `test constructor call`() {
    JavaInlayParameterHintsProvider.getInstance().isShowHintsForNewExpressions.set(true)
    check("""
public class Test {
    static class A {
      A(boolean hardName){}
    }

    void foo() {
      new A(<hint text="hardName:"/>true);
    }
}""")
  }


  fun `test constructor call disabled`() {
    JavaInlayParameterHintsProvider.getInstance().isShowHintsForNewExpressions.set(false)
    check("""
public class Test {
    static class A {
      A(boolean hardName){}
    }

    void foo() {
      new A(true);
    }
}""")
  }

  fun `test constructor call with other features`() {
    JavaInlayParameterHintsProvider.getInstance().isShowHintsForNewExpressions.set(true)
    JavaInlayParameterHintsProvider.getInstance().ignoreOneCharOneDigitHints.set(false)
    check("""
public class Test {
    static class A {
      A(boolean a1, boolean a2){}
    }

    void foo() {
      new A(true, false);
    }
}""")
  }

  fun `test parameters have comments`() {
    check("""
public class Test {
    static class A {
      A(boolean leadingComment, boolean middleWithoutComments, boolean trailingComment){}
    }

    void foo() {
      new A(/* comment not necessarily related to name */ true, <hint text="middleWithoutComments:"/>false, true /**/);
    }
}""")
  }

  fun `test optional empty`() {
    check("""
import java.util.Optional;
public class Test {
    void main() {
      foo(<hint text="s:"/>Optional.empty());
    }

    static void foo(Optional<String> s) {}
}""")
  }

  fun `test undo after typing space`() {
    check("""
class C {
  void m(int a, int b) {}
  void m2() { m(<hint text="a:"/>1, <hint text="b:"/>2); }
}
""")
    EditorTestUtil.testUndoInEditor(myFixture.editor) {
      myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf(");"))
      EditorTestUtil.executeAction(myFixture.editor, IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
      myFixture.type(' ')
      myFixture.doHighlighting()
      EditorTestUtil.executeAction(myFixture.editor, IdeActions.ACTION_UNDO)
      myFixture.doHighlighting()
      myFixture.checkResultWithInlays("""
class C {
  void m(int a, int b) {}
  void m2() { m(<hint text="a:"/>1, <hint text="b:"/><caret>2); }
}
""")
    }
  }


  fun `test same argument and parameter names`() {
    JavaInlayParameterHintsProvider.getInstance().isShowHintWhenExpressionTypeIsClear.set(true)
    JavaInlayParameterHintsProvider.getInstance().showIfMethodNameContainsParameterName.set(false)
    check("""
class A {
    static class ClassA {
        static String getName() {
            return "Asd";
        }
    }

    static void testHints(ClassA entity, String name) {

    }

    public static void main(String[] args) {
        ClassA entity = new ClassA();
        testHints(entity, ClassA.getName());
    }
}
""")
  }

  fun getHints(): List<String> {
    val document = myFixture.getDocument(myFixture.file)
    val manager = ParameterHintsPresentationManager.getInstance()
    return myFixture.editor
      .inlayModel
      .getInlineElementsInRange(0, document.textLength)
      .mapNotNull { manager.getHintText(it) }
  }

  
  private fun assertSingleInlayWithText(expectedText: String) {
    val inlays = ParameterHintsPresentationManager.getInstance().getParameterHintsInRange(
      editor,
      0,
      editor.document.textLength
    )
    assertThat(inlays).hasSize(1)
    val realText = getHintText(inlays[0])
    assertThat(realText).isEqualTo(expectedText)
  }

  fun getHintText(inlay: Inlay<*>): String {
    return ParameterHintsPresentationManager.getInstance().getHintText(inlay)
  }

}