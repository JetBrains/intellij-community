/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import java.io.*;
class Test {
  interface InterfA {
    <error descr="'foo(T)' is already defined in 'Test.InterfA'"><T extends Cloneable & Iterable> void foo(T x)</error>;

    <error descr="'foo(T)' is already defined in 'Test.InterfA'"><T extends Iterable & Cloneable> void foo(T x)</error>;
  }

  abstract class A<T extends Throwable> {
    abstract <T extends Comparable<?> & Serializable> void foo(T x, A<?> y);

    abstract <T extends Serializable & Comparable<?>> void foo(T x, A<? extends Cloneable> y);
  }

 /* abstract class B<T extends Throwable> {
    abstract <T extends Comparable<?> & Serializable> void foo(T x, B<?> y);

    abstract <T extends Serializable & Comparable<?>> void foo(T x, B<? extends Throwable> y);
  }


  abstract class C<T extends Throwable & Serializable> {
    abstract <T extends Comparable<?> & Serializable> void foo(T x, C<? extends Serializable> y);

    abstract <T extends Serializable & Comparable<?>> void foo(T x, C<? extends Throwable> y);
  }*/

  abstract class D<T extends Throwable & Runnable> {
    <error descr="'foo(T, D<? extends Runnable>)' clashes with 'foo(T, D<? extends Throwable>)'; both methods have same erasure">abstract <T extends Serializable & Comparable<?>> void foo(T x, D<? extends Runnable> y)</error>;

    abstract <T extends Serializable & Comparable<?>> void foo(T x, D<? extends Throwable> y);
  }


  interface IA {}
  interface IB {}
  void testExtendsOrder() {
    class E<T extends IA & IB> {
      <error descr="'foo(E<? extends IA>)' clashes with 'foo(E<? extends IB>)'; both methods have same erasure">void foo(E<? extends IA> x)</error> {}
      void foo(E<? extends IB> x) {}
    }
  }

  abstract class F<T extends Throwable> {
    <error descr="'foo(F<?>)' clashes with 'foo(F<? extends Throwable>)'; both methods have same erasure">abstract void foo(F<?> y)</error>;

    abstract void foo(F<? extends Throwable> y);
  }
}
