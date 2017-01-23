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

import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.lang.java.JavaLanguage

class JavaInlayParameterHintsTest : InlayParameterHintsTest() {

  fun check(text: String) {
    checkInlays("A.java", text)
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

  configure(<hint text="testNow"/>true, <hint text="times"/>555, <hint text="pii"/>3.141f, <hint text="title"/>"Huge Title", <hint text="terminate"/>'c', <hint text="file"/>null);
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
   assertEquals(<hint text="expected"/>"fooo", message);

   String title = "TT";
   show(title, <hint text="message"/>"Hi");
 }

 public void assertEquals(String expected, String actual) {}
 public void show(String title, String message) {}

}""")
  }
  

  fun `test no hints for generic builders`() {
    check("""
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
    testBooleanVarargs(<hint text="test"/>13, <hint text="...booleans"/>false);
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
    testBooleanVarargs(<hint text="test"/>13);
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
    testBooleanVarargs(<hint text="test"/>13, <hint text="...booleans"/>false, true, false);
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
    count(<hint text="test"/>100, <hint text="boo"/>false, <hint text="seq"/>"Hi!");
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
    count(<hint text="test"/>-1, obj);
    count(<hint text="test"/>+1, obj);
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
    main(<hint text="isActive"/>true,<hint text="requestFocus"/>false, /*comment*/<hint text="xoo"/>2);
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
    
    clearStatus(<hint text="updatedRecently"/>false);
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
  void compare(E o1, E o2) {}
}


public class Test {
  public void main(QCmp<Integer> c, QList<String> l) {
    c.compare(<hint text="o1"/>0, /** ddd */<hint text="o2"/>3);
    l.add(<hint text="query"/>1, <hint text="obj"/>"uuu");
  }
}
""")
  }

  fun `test inline constructor literal arguments names`() {
    check("""
public class Test {

  public void main() {
    System.out.println("AAA");
    Checker r = new Checker(<hint text="isActive"/>true, <hint text="requestFocus"/>false) {
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
    Test t = new Test(<hint text="counter"/>10, <hint text="shouldTest"/>false);
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
    testBooleanVarargs(test, <hint text="...booleans"/>isCheck, true, isOk);
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
    check(<hint text="x"/>10, <hint text="paramNameLength"/>1000);
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
    check(<hint text="beginIndex"/>10, <hint text="endIndex"/>1000, s);
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
    String.format("line", "eee", "www");
  }
  
}
""")
  }

  fun `test inline common name pair if more that 2 args xxx`() {
    check("""
public class VarArgTest {
  
  public void main() {
    check(<hint text="beginIndex"/>10, <hint text="endIndex"/>1000, <hint text="x"/>"su");
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
    check(<hint text="test"/>this, <hint text="endIndex"/>1000);
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
    createContent(<hint text="manager"/>null);
    createNewContent(<hint text="test"/>this);
  }

  Content createContent(DockManager manager) {}
  Content createNewContent(Test test) {}

}
interface DockManager {}
interface Content {}
""")
  }

  fun `test do not inline builder pattern`() {
    check("""
class Builder {
  void await(boolean value) {}
  Builder bwait(boolean xvalue) {}
  Builder timeWait(int time) {}
}

class Test {
  
  public void test() {
    Builder builder = new Builder();
    builder.await(<hint text="value"/>true);
    builder.bwait(false).timeWait(100);
  }
  
}
""")
  }

  fun `test builder method only method with one param`() {
    check("""
class Builder {
  Builder qwit(boolean value, String sValue) {}
  Builder trew(boolean value) {}
}

class Test {
  public void test() {
    Builder builder = new Builder();
    builder
    .trew(false)
    .qwit(<hint text="value"/>true, <hint text="sValue"/>"value");
  }
}
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
    blah(<hint text="a"/>1, <hint text="b"/>2);
    int z = 2;
    draw(<hint text="x"/>10, <hint text="y"/>20, z);
    int x = 10;
    int y = 12;
    drawRect(x, y, <hint text="w"/>10, <hint text="h"/>12);
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
  }
  
  void set(int newValue) {}
  
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
    try {
      ParameterHintsPassFactory.setDebug(true)
      check("""
class Test {
  void test() {
    xxx(<hint text="followTheSum"/>100);
    check(<hint text="isShow"/>1 + 1);
    check(<hint text="isShow"/>1 + 1 + 1);
    yyy(<hint text="followTheSum"/>200);
  }
  void check(int isShow) {}
  void xxx(int followTheSum) {}
  void yyy(int followTheSum) {}
}
""")
    }
    finally {
      ParameterHintsPassFactory.setDebug(false)
    }
  }

  fun `test incorrect pattern`() {
    ParameterNameHintsSettings.getInstance().addIgnorePattern(JavaLanguage.INSTANCE, "")
    check("""
class Test {
  void test() {
    check(<hint text="isShow"/>1000);  
  }
  void check(int isShow) {}
}
""")
  }

  fun `test do not show hint for name contained in method`() {
    ParameterNameHintsSettings.getInstance().isDoNotShowIfMethodNameContainsParameterName = true
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
    ParameterNameHintsSettings.getInstance().isDoNotShowIfMethodNameContainsParameterName = true
    check("""
class Test {
  void main() {
    timeoutExecution(<hint text="timeout"/>1000, <hint text="message"/>"xxx");
    createSpace(<hint text="space"/>true, <hint text="a"/>10);
  }
  void timeoutExecution(int timeout, String message) {}
  void createSpace(boolean space, int a) {}
}
""")
  }

  fun `test show same params`() {
    ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType = true
    check("""
class Test {
  void main() {
    String c = "c";
    String d = "d";
    test(<hint text="parent"/>c, <hint text="child"/>d);
  }
  void test(String parent, String child) {
  }
}
""")
  }

  fun `test show triple`() {
    ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType = true
    check("""
class Test {
  void main() {
    String c = "c";
    test(<hint text="parent"/>c, <hint text="child"/>c, <hint text="grandParent"/>c);
  }
  void test(String parent, String child, String grandParent) {
  }
}
""")
  }

  fun `test show couple of doubles`() {
    ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType = true
    check("""
class Test {
  void main() {
    String c = "c";
    String d = "d";
    int v = 10;
    test(<hint text="parent"/>c, <hint text="child"/>d, <hint text="vx"/>v, <hint text="vy"/>v);
  }
  void test(String parent, String child, int vx, int vy) {
  }
}
""")
  }

  fun `test show ambigous`() {
    check("""
class Test {
  void main() {
    test(<hint text="a"/>10, x);
  }
  void test(int a, String bS) {}
  void test(int a, int bI) {}
}
""")
  }

  fun `test show ambiguous constructor`() {
    check("""
class Test {
  void main() {
    new X(<hint text="a"/>10, x);
  }
}

class X {
  X(int a, int bI) {}
  X(int a, String bS) {}
}
""")
  }

}