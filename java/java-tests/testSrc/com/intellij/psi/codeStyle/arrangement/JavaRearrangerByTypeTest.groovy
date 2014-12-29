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
package com.intellij.psi.codeStyle.arrangement

import org.junit.Before

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.*

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:45 PM
 */
class JavaRearrangerByTypeTest extends AbstractJavaRearrangerTest {

  @Before
  void setUp() {
    super.setUp()
    commonSettings.BLANK_LINES_AROUND_METHOD = 0
    commonSettings.BLANK_LINES_AROUND_CLASS = 0
  }

  void "test fields before methods"() {
    doTest(
      initial: '''\
class Test {
  public void test() {}
   private int i;
}
class Test2 {
public void test() {
}
    private int i;
  private int j;
}''',
      rules: [rule(FIELD)],
      expected: '''\
class Test {
   private int i;
  public void test() {}
}
class Test2 {
    private int i;
  private int j;
public void test() {
}
}'''
    )
  }

  void "test anonymous class at field initializer"() {
    doTest(
      initial: '''\
class Test {
  private Object first = new Object() {
    int inner1;
    public String toString() { return "test"; }
    int inner2;
  };
  public Object test(Object ... args) {
    return null;
  }
  private Object second = test(test(new Object() {
    public String toString() {
      return "test";
    }
    private Object inner = new Object() {
      public String toString() { return "innerTest"; }
    };
  }));
}''',
      expected: '''\
class Test {
  private Object first = new Object() {
    int inner1;
    int inner2;
    public String toString() { return "test"; }
  };
  private Object second = test(test(new Object() {
    private Object inner = new Object() {
      public String toString() { return "innerTest"; }
    };
    public String toString() {
      return "test";
    }
  }));
  public Object test(Object ... args) {
    return null;
  }
}''',
      rules: [rule(FIELD)]
    )
  }

  void "test anonymous class at method"() {
    doTest(
      initial: '''\
class Test {
   void declaration() {
     Object o = new Object() {
       private int test() { return 1; }
       String s;
     }
   }
   double d;
   void call() {
     test(test(1, new Object() {
       public void test() {}
       int i;
     });
   }
}''',
      expected: '''\
class Test {
   double d;
   void declaration() {
     Object o = new Object() {
       String s;
       private int test() { return 1; }
     }
   }
   void call() {
     test(test(1, new Object() {
       int i;
       public void test() {}
     });
   }
}''',
      rules: [rule(FIELD)]
    )
  }

  void "test inner class interface and enum"() {
    doTest(
      initial: '''\
class Test {
   enum E { ONE, TWO }
   class Inner {}
   interface Intf {}
}''',
      expected: '''\
class Test {
   interface Intf {}
   enum E { ONE, TWO }
   class Inner {}
}''',
      rules: [rule(INTERFACE),
              rule(ENUM),
              rule(CLASS)]
    )
  }

  void "test ranges"() {
    doTest(
      initial: '''\
class Test {
  void outer1() {}
<range>  String outer2() {}
  int i;</range>
  void test() {
    method(new Object() {
      void inner1() {}
      Object field = new Object() {
<range>        void inner2() {}
        String s;</range>
        Integer i;
      }
    });
  }
}''',
      expected: '''\
class Test {
  void outer1() {}
  int i;
  String outer2() {}
  void test() {
    method(new Object() {
      void inner1() {}
      Object field = new Object() {
        String s;
        void inner2() {}
        Integer i;
      }
    });
  }
}''',
      rules: [rule(FIELD)]
    )
  }

  void "test methods and constructors"() {
    doTest(
      initial: '''\
class Test {
  abstract void method1();
  Test() {}
  abstract void method2();
}''',
      expected: '''\
class Test {
  Test() {}
  abstract void method1();
  abstract void method2();
}''',
      rules: [rule(CONSTRUCTOR), rule(METHOD)])
  }

  void "test multiple fields in one row"() {
    doTest(
      initial: '''\
class Test {
  private long l;
  public int i, j;
  protected float f;
}''',
      rules: [rule(PUBLIC), rule(PROTECTED)],
      expected: '''\
class Test {
  public int i, j;
  protected float f;
  private long l;
}'''
    )
  }
  
  void "test multiline multiple field variables declaration"() {
    doTest(
      initial:  '''\
class Test {
  private String a1,
                 a2;
  public String a3;
}''',
      rules: [rule(FIELD, PUBLIC), rule(FIELD, PRIVATE)],
      expected: '''\
class Test {
  public String a3;
  private String a1,
                 a2;
}'''
    )
  }

  void "test multiline multiple field variables declaration with initializers"() {
    doTest(
      initial:  '''\
class Test {
  private String a1 = "one",
                 a2 = "two";
  public String a3;
}''',
      rules: [rule(FIELD, PUBLIC), rule(FIELD, PRIVATE)],
      expected: '''\
class Test {
  public String a3;
  private String a1 = "one",
                 a2 = "two";
}'''
    )
  }
  
  void "test incomplete multiple multiline field "() {
    doTest(
      initial:  '''\
class Test {
  private String a1,
                 a2
  public String a3;
}''',
      rules: [rule(FIELD, PUBLIC), rule(FIELD, PRIVATE)],
      expected: '''\
class Test {
  public String a3;
  private String a1,
                 a2
}'''
    )    
  }

  void "test fields with comments"() {
    doTest(
      initial: '''\
class Test {
  int h1, /** h1 */
      h2;
  int f1, // f1
      f2; // f2
  int g1, /* g1 */
      g2;
  int e1, e2; // ee
  int d; /* c-style
            multi-line comment */
  int b; /* c-style single line comment */
  int c; // comment
  int a;
}''',
      rules: [ruleWithOrder(BY_NAME, rule(FIELD))],
      expected: '''\
class Test {
  int a;
  int b; /* c-style single line comment */
  int c; // comment
  int d; /* c-style
            multi-line comment */
  int e1, e2; // ee
  int f1, // f1
      f2; // f2
  int g1, /* g1 */
      g2;
  int h1, /** h1 */
      h2;
}'''
    )
  }
  
  void "test anonymous class and siblings"() {
    doTest(
      initial: '''\
class Test {
  void test() {
    new MyClass(new Object() {
      @Override
      public String toString() {
        return null;
      }
    }) {
      @Override
      public int hashCode() {
        return 1;
      }
      private int field;
    }
  };
}''',
      rules: [rule(FIELD), rule(METHOD)],
      expected: '''\
class Test {
  void test() {
    new MyClass(new Object() {
      @Override
      public String toString() {
        return null;
      }
    }) {
      private int field;
      @Override
      public int hashCode() {
        return 1;
      }
    }
  };
}'''
    )
  }

  void "test multiple elements at the same line"() {
    doTest(
      initial: '''\
class Test {
  int i;int getI() {
    return i;
  }int j;int getJ() {
    return j;
  }
}''',
      rules: [rule(FIELD), rule(METHOD)],
      expected: '''\
class Test {
  int i;int j;int getI() {
    return i;
  }int getJ() {
    return j;
  }
}'''
    )
  }

  void "test IDEA-124077 Enum code reformat destroys enum"() {
    doTest(
      initial: '''
public enum ErrorResponse {

    UNHANDLED_EXCEPTION,
    UNHANDLED_BUSINESS,
    ACCOUNT_NOT_VALID,
    ACCOUNT_LATE_CREATION;

    public void test() {}
    public int t;

    public long l;
    private void q() {}
}
''',
      expected: '''
public enum ErrorResponse {

    UNHANDLED_EXCEPTION,
    UNHANDLED_BUSINESS,
    ACCOUNT_NOT_VALID,
    ACCOUNT_LATE_CREATION;

    public void test() {}
    private void q() {}
    public int t;
    public long l;
}
''',
      rules: [
        rule(METHOD),
        rule(FIELD)
      ]
    )
  }

  void "test parameterized class"() {
    doTest(
      initial: '''\
public class Seq<T> {

    public Seq(T x) {
    }

    public Seq() {}

    static <T> Seq<T> nil() {
        return new Seq<T>();
    }

    static <V> Seq<V> cons(V x) {
        return new Seq<V>(x);
    }

    int filed;
}
''',
      expected: '''\
public class Seq<T> {

    int filed;

    public Seq(T x) {
    }

    public Seq() {}
    static <T> Seq<T> nil() {
        return new Seq<T>();
    }
    static <V> Seq<V> cons(V x) {
        return new Seq<V>(x);
    }
}
''',
      rules: [
        rule(FIELD)
      ]
    )
  }

  void "test overridden method is matched by overridden rule"() {
    doTest(
      initial: '''\
class A {
  public void test() {}
  public void run() {}
}

class B extends A {

  public void infer() {}

  @Override
  public void test() {}

  private void fail() {}

  @Override
  public void run() {}

  private void compute() {}

  public void adjust() {}

}
''',
      expected: '''\
class A {
  public void test() {}
  public void run() {}
}

class B extends A {

  public void infer() {}
  public void adjust() {}
  private void fail() {}
  private void compute() {}
  @Override
  public void test() {}
  @Override
  public void run() {}

}
''',
      rules: [rule(PUBLIC, METHOD), rule(PRIVATE, METHOD), rule(OVERRIDDEN)]
    )
  }

  void "test overridden method is matched by method rule if no overridden rule found"() {
    doTest(
      initial: '''\
class A {
  public void test() {}
  public void run() {}
}

class B extends A {

  public void infer() {}

  @Override
  public void test() {}

  private void fail() {}

  @Override
  public void run() {}

  private void compute() {}

  public void adjust() {}

}
''',
      expected: '''\
class A {
  public void test() {}
  public void run() {}
}

class B extends A {

  public void infer() {}

  @Override
  public void test() {}
  @Override
  public void run() {}
  public void adjust() {}
  private void fail() {}
  private void compute() {}

}
''',
      rules: [rule(PUBLIC, METHOD), rule(PRIVATE, METHOD)]
    )
  }

  void "test initializer block after fields"() {
    doTest(
      initial: '''\
public class NewOneClass {

    {
        a = 1;
    }

    int a;

    {
        b = 5;
    }

    int b;

}
''',
      expected: '''\
public class NewOneClass {

    int a;
    int b;

    {
        a = 1;
    }

    {
        b = 5;
    }

}
''',
      rules: [rule(FIELD), rule(INIT_BLOCK)]
    )
  }

  void "test static initializer block"() {
    doTest(
      initial: '''\
public class NewOneClass {

    static {
        a = 1;
    }

    static int a;

    {
        b = 5;
    }

    int b;

}
''',
      expected: '''\
public class NewOneClass {

    static int a;

    static {
        a = 1;
    }

    int b;

    {
        b = 5;
    }

}
''',
      rules: [rule(STATIC, FIELD), rule(STATIC, INIT_BLOCK), rule(FIELD), rule(INIT_BLOCK)]
    )
  }


}
