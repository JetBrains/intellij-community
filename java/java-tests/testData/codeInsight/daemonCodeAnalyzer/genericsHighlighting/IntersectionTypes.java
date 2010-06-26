import java.io.*;
import java.util.*;

class Test {
    <T> List<T> asList (T... ts) {
      ts.hashCode();
      return null;
    }

    void foo() {
        <error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<? extends java.io.Serializable & java.lang.Comparable<?>>>', required: 'java.util.List<java.lang.Class<? extends java.io.Serializable>>'">List<Class<? extends Serializable>> l = this.asList(String.class, Integer.class);</error>
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
        Maps.asMap(Maps.entry(fieldName(), "Test"),
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
    <error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<? extends java.io.Serializable & java.lang.Comparable<?>>>', required: 'java.util.List<java.lang.Class<? extends java.io.Serializable>>'">List<Class<? extends Serializable>> SIMPLE_TYPES =
asList(String.class, Integer.class ,Long.class, Double.class, /*Date.class,*/
Boolean.class, Boolean.TYPE /*,String[].class */ /*,BigDecimal.class*/);</error>


      public static final List<Class<? extends Serializable>> SIMPLE_TYPES_INFERRED =
  asList(String.class, Integer.class ,Long.class, Double.class, /*Date.class,*/
  Boolean.class, Boolean.TYPE ,String[].class  /*,BigDecimal.class*/);


}
///////////////////////
class Axx {
  <T extends Runnable> T a() {
    <error descr="Incompatible types. Found: 'T', required: 'java.lang.String'">String s = a();</error>
    s.hashCode();
    return null;
  }
}
///////////////
interface L {}
public class MaximalType  {
    public static <T> T getParentOfType(Class<? extends T>... classes) {
       classes.hashCode();
       return null;
    }
    {
        getParentOfType(M2.class, M.class);
    }
}
class M extends MaximalType implements L{}
class M2 extends MaximalType implements L{}
/////////////