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
abstract class C {
  private static class NE { }
  private static class E extends Exception { public String s; }
  private static class E1 extends E { }
  private static class E2 extends E { }
  private static class E3 extends E { }
  private static class E4 extends E { }
  private static class RE extends RuntimeException { }
  private interface I<T> { void i(); }
  private static class IE1 extends E implements I<Integer> { public void i() { } }
  private static class IE2 extends E implements I<Long> { public void i() { } }
  private static class F<X> { F(X x) { } }

  abstract void f() throws E1, E2;
  abstract void g() throws IE1, IE2;

  <T extends Throwable> void m() {
    try { f(); } catch (E1 | E2 e) { }
    try { f(); } catch (E2 | E1 e) { e.printStackTrace(); System.out.println(e.s); }
    try { f(); } catch (E2 | E1 e) { } catch (E e) { } catch (RE e) { }
    try { g(); } catch (IE1 | IE2 e) { E ee = e; I ii = e; e.i(); }
    try { g(); } catch (IE1 | IE2 e) { F<?> f = new F<>(e); }
    try { g(); } catch (IE1 | IE2 e) { new F<I<? extends Number>>(e); }

    try { } catch (<error descr="Incompatible types. Found: 'C.RE | C.NE', required: 'java.lang.Throwable'">RE | NE e</error>) { }
    try { } catch (<error descr="Incompatible types. Found: 'C.RE | C.NE[]', required: 'java.lang.Throwable'">RE | NE e[]</error>) { }
    try { f(); } catch (<error descr="Incompatible types. Found: 'C.E | T[]', required: 'java.lang.Throwable'">E | T e[]</error>) { } catch(E e) { }
    try { f(); } catch (E | <error descr="Cannot catch type parameters">T</error> e) { }

    try { f(); } catch (<error descr="Types in multi-catch must be disjoint: 'C.E1' is a subclass of 'C.E'">E1</error> | E ignore) { }
    try { f(); } catch (E | <error descr="Types in multi-catch must be disjoint: 'C.E1' is a subclass of 'C.E'">E1</error> ignore) { }
    try { f(); } catch (<error descr="Types in multi-catch must be disjoint: 'C.E' is a subclass of 'C.E'">E</error> | E ignore) { }

    try { f(); } catch (E1 | E2 | <error descr="Exception 'C.E3' is never thrown in the corresponding try block">E3</error> e) { }
    try { f(); } catch (<error descr="Exception 'C.E3' is never thrown in the corresponding try block">E3</error> | <error descr="Exception 'C.E4' is never thrown in the corresponding try block">E4</error> | RE e) { } catch (E e) { }
    try { <error descr="Unhandled exceptions: C.E1, C.E2">f</error>(); } catch (E3 | E4 | RE e) { }

    try { f(); } catch (E e) { } catch (<error descr="Exception 'C.E1' has already been caught">E1</error> | <error descr="Exception 'C.E3' has already been caught">E3</error> e) { }
    try { f(); } catch (E1 | E2 e) { } catch (<error descr="Exception 'C.E2' has already been caught">E2</error> e) { }

    try { f(); } catch (E1 | E2 e) { <error descr="Incompatible types. Found: 'C.E1 | C.E2', required: 'C.E2'">E2 ee = e;</error> }
    try { f(); } catch (E1 | E2 e) { <error descr="Cannot assign a value to final variable 'e'">e</error> = new E1(); }
    try { f(); } catch (E1 | E2 e) { <error descr="Incompatible types. Found: 'C.E', required: 'C.E1 | C.E2'">e = new E()</error>; }

    try { g(); }
    catch (IE1 | IE2 e) {
      Class<? extends E> clazz1 = e.getClass();
      <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends C.E>>', required: 'java.lang.Class<? extends C.IE1>'">Class<? extends IE1> clazz2 = e.getClass();</error>
      <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends C.E>>', required: 'java.lang.Class<? extends C.I>'">Class<? extends I> clazz3 = e.getClass();</error>
    }

    try { f(); }
    catch (<error descr="Incompatible types. Found: 'int | C.E', required: 'java.lang.Throwable'">int | E e</error>) { }
  }
}

class D {
  static class E extends Exception { }
  static interface I { void i(); }
  static class E1 extends E implements I { public void i() { } }
  static class E2 extends E implements I { public void i() { } }

  void m(boolean f) {
    try {
      if (f)
        throw new E1();
      else
        throw new E2();
    } catch (E1|E2 e) {
      System.out.println(e);
    }
  }
}