import java.util.ArrayList;

class Reference<<warning descr="Type parameter 'T' is never used">T</warning>> {
}
class WeakReference<T> extends Reference<T> {
}
class Item<<warning descr="Type parameter 'Key' is never used">Key</warning>, T> extends WeakReference<T> {
 {
   Reference<T> ref = null;
   Item item = (Item) ref;
   equals(item);
 }
}

// assign raw to generic are allowed
class a<E> {
 void f(a<E> t){
    t.hashCode();
 }
}
class b  {
  a<b> f(a raw) {
   a<?> unbound = raw;
   raw = unbound;

   a<Integer> generic = <warning descr="Unchecked assignment: 'a' to 'a<java.lang.Integer>'">raw</warning>;
   <warning descr="Unchecked call to 'f(a<E>)' as a member of raw type 'a'">raw.f</warning>(raw);
   <warning descr="Unchecked call to 'f(a<E>)' as a member of raw type 'a'">raw.f</warning>(generic);
   generic.f(<warning descr="Unchecked assignment: 'a' to 'a<java.lang.Integer>'">raw</warning>);
   generic.f(generic);
   generic.f<error descr="'f(a<java.lang.Integer>)' in 'a' cannot be applied to '(a<java.lang.String>)'">(new a<String>())</error>;
   generic = <warning descr="Unchecked assignment: 'a' to 'a<java.lang.Integer>'">raw</warning>;


   return <warning descr="Unchecked assignment: 'a' to 'a<b>'">raw</warning>;
  }
}

class List<T> {
   <V> V[] toArray (V[] vs) { return vs; }
   void add(T t) {
     t.hashCode();
   }
}

class c {
  /*String[] f () {
    List l = new List();
    error descr="Incompatible types. Found: 'java.lang.Object[]', required: 'java.lang.String[]'">return l.toArray (new String[0]);</error
  }*/
  
  String[] g () {
    List<String> l = new List<String>();
    return l.toArray (new String[0]);
  }
}

class d {
    class Y <<warning descr="Type parameter 'T' is never used">T</warning>> {
    }

    class Z <<warning descr="Type parameter 'T' is never used">T</warning>> extends Y<Y> {
    }

    class Pair <X> {
        void foo(Y<? extends X> y) {
          y.hashCode();
        }
    }

    Pair<Z> pair;

    void bar(Y<? extends Y> y) {
        pair.foo<error descr="'foo(d.Y<? extends d.Z>)' in 'd.Pair' cannot be applied to '(d.Y<capture<? extends d.Y>>)'">(y)</error>;
    }
}

class e {
    String foo () {
        MyList myList = new MyList();
        <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">return myList.get(0);</error>
    }

    static class MyList<<warning descr="Type parameter 'T' is never used">T</warning>> extends ArrayList<String>{
    }
}

class ccc {
    static Comparable<? super ccc> f() {
        return <warning descr="Unchecked assignment: 'java.lang.Comparable' to 'java.lang.Comparable<? super ccc>'">new Comparable () {
            public int compareTo(final Object o) {
                return 0;
            }
        }</warning>;
    }
}

class ddd<COMP extends ddd> {
    COMP comp;
    ddd foo() {
        return comp; //no unchecked warning is signalled here
    }
}

class G1<T> {
  T t;
}
class G2<T> {
    T t;
    
    static ArrayList<G1> f() {
        return null;
    }
}

class Inst {
    static void f () {
        G2<G1<String>> g2 = new G2<G1<String>>();
        for (<warning descr="Unchecked assignment: 'G1' to 'G1<java.lang.String>'">G1<String> g1</warning> : g2.f()) {
          g1.toString();
        }
    }
}

class A111<T> {
  T t;
  <V> V f(V v) {
    return v;
  }

  String g(A111 a) {
    //noinspection unchecked
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">return a.f("");</error>
  }
}

class A1 {
  <V> V f(V v) {
    return v;
  }
}

class A11<T> extends A1 {
    T t;

    //this is OK, type parameters of base class are not raw
    String s = new A11().f("");
}

//IDEADEV-26163
class Test1<X> {
  X x;
  java.util.ArrayList<Number> foo = new java.util.ArrayList<Number>();
  public static Number foo() {
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.Number'">return new Test1().foo.get(0);</error>
  }
}
//end of IDEADEV-26163


///////////////  signatures in non-parameterized class are not erased
public class C3  {
    public int get(Class<?> c) {
        return 0;
    }
}

class Cp<T> extends C3 {
  public T i;
}
class C extends Cp/*<C>*/ {
    @Override
    public int get(Class<?> c) {
        return 0;
    }
}
//////////////