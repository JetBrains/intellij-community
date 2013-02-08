/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import java.lang.Object;

class C {
  @interface TestAnnotation {
    int[] value();
  }

  @TestAnnotation({0, 1<warning descr="Trailing comma in annotation array initializer may cause compilation error in some Javac versions (e.g. JDK 5 and JDK 6).">,</warning>})
  void m() { }

  class A<T> {
    class B<V> {
      void m(T t, V v) { System.out.println(t + ", " + v); }
    }
  }

  void m(Object o) {
    if (o instanceof A<?>.B<?>) {
      final A<?>.B<?> b = (A<warning descr="Generics in qualifier reference may cause compilation error in some Javac versions (e.g. JDK 5 and JDK 6)."><?></warning>.B<?>)o;
      b.m(null, null);
    }
  }
}