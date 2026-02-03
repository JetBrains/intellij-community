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

// illegal method calls

class A {
    private class B {
      Object o = super<error descr="'.' expected">;</error>
    }
    private B b = null;
    A(A a)
    {
        new A(a).new B();
        B b = new A(a).b;
        AA aa = (AA) a;
        AA.SAA saa = aa.new SAA();
        AA.SAA saa1 = <error descr="'AA' is not an enclosing class">new AA.SAA()</error>;
    }
}

class AA extends A {
    private AA aa;
    AA(A a) {
        super(a);
    }
    class SAA {}

    void f() {
      new AA.SAA();
      new SAA();
      AA.this.aa.new SAA();

      class MyAA extends AA {
        public MyAA(A a) {
          super(a);
        }
      }
    }
}

class AX {
    class B {
    }
}
class CX {
    {
        <error descr="'AX' is not an enclosing class">new AX.B()</error>;
    }
}



class c {
    c() {}

    class inner {
        class ininner {}
    }

    static void f() {
        <error descr="'c.this' cannot be referenced from a static context">new inner()</error>;
    }

    static {
        <error descr="'c.this' cannot be referenced from a static context">new inner()</error>;
    }
}


class A1 {
  void f() {}
}
class B1 {
  void f() {
    A1.<error descr="Non-static method 'f()' cannot be referenced from a static context">f</error>();
  }
}

class AAAA implements java.io.Serializable
{
    public AAAA ()
    {
        super(); // here
    }
}

class DCC {
    public DCC(int i) {
    }

    public DCC(int i, int z) {
        <error descr="Method call expected">DCC(i)</error>;
    }
    void f() {
        <error descr="Method call expected">DCC(1)</error>;
        new DCC(1);
    }
    {
        <error descr="Qualifier must be an expression">java</error>.toString();
    }
}

class ThisExpression {
    static String foo() {
        System.out.println(<error descr="'ThisExpression.this' cannot be referenced from a static context">this</error>);
        return <error descr="'ThisExpression.super' cannot be referenced from a static context">super</error>.toString();
    }
}
