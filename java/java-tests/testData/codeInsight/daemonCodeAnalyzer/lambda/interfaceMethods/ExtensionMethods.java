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

class C {
  interface I {
    int i = 42;
    default void m() { }
  }

  interface II extends I {
    default void m() {
      I.super.m();
      <error descr="Unqualified super reference is not allowed in extension method">super.<error descr="Cannot resolve method 'm()'">m</error></error>();

      System.out.println(<error descr="'C.I' is not an enclosing class">I.super</error>.i);
      System.out.println(<error descr="Unqualified super reference is not allowed in extension method">super.<error descr="Cannot resolve symbol 'i'">i</error></error>);
    }

    void ma();
  }

  void test() {
    new I(){}.m();

    new II() {
      public void ma() {
        <error descr="'C.I' is not an enclosing class">I.super</error>.m();
        II.super.m();
        <error descr="Abstract method 'ma()' cannot be accessed directly">II.super.ma()</error>;
      }
    }.m();
  }
}

class D {
  <error descr="Extension methods can only be used within an interface">default void m()</error> { }
}

interface IllegalMods {
  void m1()<error descr="'{' or ';' expected"> </error>default<error descr="Identifier or type expected">;</error> <error descr="Not allowed in interface">{ }</error>

  <error descr="Static methods in interfaces should have a body">static void m2()</error>;
  <error descr="Illegal combination of modifiers: 'static' and 'default'">static</error> <error descr="Illegal combination of modifiers: 'default' and 'static'">default</error> void m3() { }

  <error descr="Illegal combination of modifiers: 'abstract' and 'default'">abstract</error> <error descr="Illegal combination of modifiers: 'default' and 'abstract'">default</error> void m4() { }

  <error descr="Extension method should have a body">default void m5()</error>;

  <error descr="Modifier 'default' not allowed here">default</error> int i;
  <error descr="Modifier 'default' not allowed here">default</error> interface X { }
}