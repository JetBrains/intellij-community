import java.io.*;
import java.util.*;

class Test {
    <T> List<T> asList (T... ts) {
      ts.hashCode();
      return null;
    }

    void foo() {
        <error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<? extends java.io.Serializable & java.lang.Comparable<? extends java.io.Serializable & java.lang.Comparable<?>>>>', required: 'java.util.List<java.lang.Class<? extends java.io.Serializable>>'">List<Class<? extends Serializable>> l = <warning descr="Unchecked generics array creation for varargs parameter">this.asList</warning>(String.class, Integer.class);</error>
        l.size();
        List<? extends Object> objects = this.asList(new String(), new Integer(0));
        objects.size();
    }
}

//SUN BUG ID 5034571
interface I1 {
    void i1();
}

class G1 <T extends I1> {
    T get() { return null; }
}

interface I2 {
    void i2();
}

class Main {
    void f2(G1<? extends I2> g1) {
        g1.get().i1(); // this should be OK
        g1.get().i2(); // this should also be OK
    }
}

//IDEADEV4200: this code is OK
interface I11 {
    String i1();
}

interface I21 {
    String i2();
}

interface A<T> {
    T some();
}

interface B<T extends I11 & I21> extends A<T> {

}

class User {

    public static void main(B<?> test) {
        System.out.println(test.some().i1());
        System.out.println(test.some().i2());
    }
}
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

//end of IDEADEV4200

//IDEADEV-4214
interface Keyable<K> {
    /**
     * @return the key for the instance.
     */
    public K getKey();
}

abstract class Date implements java.io.Serializable, Cloneable, Comparable<Date> {

}

class Maps {
    public static class MapEntry<K, V> implements Map.Entry<K, V> {
        K k;
        V v;

        public K getKey() {
            return k;
        }

        public V getValue() {
            return v;
        }

        public V setValue(V value) {
            return v = value;
        }

        public MapEntry(K k, V v) {
            this.k = k;
            this.v = v;
        }
    }

    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new MapEntry<K, V>(key, value);
    }

    public static <K, V> Map<K, V> asMap(Map.Entry<? extends K, ? extends V> ... <warning descr="Parameter 'entries' is never used">entries</warning>) {
        return null;
    }

    public static <K, V extends Keyable<K>> Map<K, V> asMap(V ... <warning descr="Parameter 'entries' is never used">entries</warning>) {
        return null;
    }
}

class Client {
    void f(Date d) {
        //this call should be OK
        <warning descr="Unchecked generics array creation for varargs parameter">Maps.asMap</warning>(Maps.entry(fieldName(), "Test"),
                   Maps.entry(fieldName(), 1),
                   Maps.entry(fieldName(), d));
    }

    String fieldName() {
        return null;
    }
}
//end of IDEADEV-4214

class IDEADEV25515 {
    static <T> List<T> asList (T... ts) {
      ts.hashCode();
      return null;
    }

    public static final
    <error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<? extends java.io.Serializable & java.lang.Comparable<? extends java.io.Serializable & java.lang.Comparable<?>>>>', required: 'java.util.List<java.lang.Class<? extends java.io.Serializable>>'">List<Class<? extends Serializable>> SIMPLE_TYPES =
<warning descr="Unchecked generics array creation for varargs parameter">asList</warning>(String.class, Integer.class ,Long.class, Double.class, /*Date.class,*/
Boolean.class, Boolean.TYPE /*,String[].class */ /*,BigDecimal.class*/);</error>


      public static final List<Class<? extends Serializable>> SIMPLE_TYPES_INFERRED =
  <warning descr="Unchecked generics array creation for varargs parameter">asList</warning>(String.class, Integer.class ,Long.class, Double.class, /*Date.class,*/
  Boolean.class, Boolean.TYPE ,String[].class  /*,BigDecimal.class*/);


}
///////////////////////
class Axx {
  <T extends Runnable> T a() {
    <error descr="Incompatible types. Found: 'java.lang.Runnable', required: 'java.lang.String'">String s = a();</error>
    s.hashCode();
    return null;
  }
}
///////////////
interface L {}
class MaximalType  {
    public static <T> T getParentOfType(Class<? extends T>... classes) {
       classes.hashCode();
       return null;
    }
    {
        <warning descr="Unchecked generics array creation for varargs parameter">getParentOfType</warning>(M2.class, M.class);
    }
}
class M extends MaximalType implements L{}
class M2 extends MaximalType implements L{}
/////////////


class IDEA67676 {
  interface I<<warning descr="Type parameter 'T' is never used">T</warning>> {}
  interface A<T> extends I<A<T>>{}
  interface Com2<T, U> {
    void foo(T t, U u);
  }
  interface Com1<T> {
    void foo(T t);
  }

  abstract class X {
      abstract <T> T foo(T x, T y);

      void bar(A<A2> x, A<B2> y) {
          A<? extends Com2<? extends Com2<?, ?>, ? extends Com2<?, ?>>> f = foo(x, y);
          f.hashCode();
      }

      void boo(A<A3> x, A<B3> y) {
          A<? extends Com2<? extends Com2<?, ?>, ? extends Com2<?, ?>>> f = foo(x, y);
          f.hashCode();
      }

      void baz(A<A1> x, A<B1> y) {
          A<? extends Com1<? extends Com1<?>>> f = foo(x, y);
          f.hashCode();
      }
  }

  abstract class A1 implements Com1<A1> {}
  abstract class B1 implements Com1<B1> {}

  abstract class A2 implements Com2<A2, A2> {}
  abstract class B2 implements Com2<B2, B2> {}

  abstract class A3 implements Com2<A3, B3> {}
  abstract class B3 implements Com2<B3, A3> {}
}