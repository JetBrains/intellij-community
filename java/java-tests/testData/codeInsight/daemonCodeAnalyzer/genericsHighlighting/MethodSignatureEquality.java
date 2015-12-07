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
    <T extends Cloneable & Iterable> void foo(T x);

    <T extends Iterable & Cloneable> void foo(T x);
  }

  class ANotSame {
    <T extends Cloneable & Iterable> void foo(T x){}

    <T extends Iterable & Cloneable> void foo(T x){}
  }

  class BNotSame extends ANotSame {
      @Override
      <T extends Cloneable & Iterable> void foo(T x){}
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
    <error descr="'foo(F<?>)' is already defined in 'Test.F'">abstract void foo(F<?> y)</error>;

    <error descr="'foo(F<? extends Throwable>)' is already defined in 'Test.F'">abstract void foo(F<? extends Throwable> y)</error>;
  }
}

class Ao {}

class Bo extends Ao {}

class SettingsEditor<T> {
}

abstract class RunConfigurationExtension<T extends Ao> {
    protected abstract <P extends T> SettingsEditor<P> createEditor(final P configuration);
}

class F extends RunConfigurationExtension<Bo> {

    @Override
    protected <P extends Bo> SettingsEditor<P> createEditor(P configuration) {
        return null;
    }
}
