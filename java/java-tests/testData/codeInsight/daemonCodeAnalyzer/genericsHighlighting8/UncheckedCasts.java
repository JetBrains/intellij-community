import java.util.*;

class X<<warning descr="Type parameter 'T' is never used">T</warning>> {

}

class XX<T> extends X<T> {
  Object f(X<String> x) {
    if (x != null) {
      XX<String> xx = <warning descr="Unchecked cast: 'XX' to 'XX<java.lang.String>'">(XX<String>)new XX()</warning>;
      return xx;
    }
    if (1 == 1) {
      XX<String> xx = (XX<String>)x;
      return xx;
    }
    return null;
  }
}

class eee<COMP extends eee> {
    COMP comp;
    COMP foo() {
        return <warning descr="Unchecked cast: 'eee' to 'COMP'">(COMP) new eee()</warning>;
    }
}

class AllPredicate<T>
    {
    private List<Set<? super T>> lists;

    public void e(AllPredicate that)
    {
         lists = <warning descr="Unchecked cast: 'java.util.List' to 'java.util.List<java.util.Set<? super T>>'">(List<Set<? super T>>)that.lists</warning>;
    }

    public static List<String> fff() {
        Collection<String> c = new ArrayList<String>();
        return (List<String>) c; //not unchecked
    }

    public static Comparable<Object> ggg() {
        Object time = new Object();
        return <warning descr="Unchecked cast: 'java.lang.Object' to 'java.lang.Comparable<java.lang.Object>'">(Comparable<Object>) time</warning>;
    }

    public static void foo(SortedMap<?, ?> sourceSortedMap) {
        new TreeMap<Object, Object>(<warning descr="Unchecked cast: 'java.util.Comparator<capture<? super capture<?>>>' to 'java.util.Comparator<? super java.lang.Object>'">(Comparator<? super Object>) sourceSortedMap.comparator()</warning>);
    }
}

class K { }
class L extends K { }
class M {
  public static <T extends K> L f(T t) {
     return (L) t; //this should NOT generate unchecked cast
  }
}

class UncheckedCastFalsePositive {

    public static void method(Object something) {
        if (something instanceof NumberList) {
            NumberList<? extends Number> <warning descr="Variable 'numberList' is never used">numberList</warning> = (NumberList<?  extends Number>) something;
        }

    }

    public static class NumberList<E extends Number> extends ArrayList<E> {
    }

}

class IDEA21547 {
  class O {}
  class A<<warning descr="Type parameter 'T' is never used">T</warning>> {}
  class B<K extends O> extends A<K>{}

  public void bar(A<? extends O> a, B<O> b) {
    b = <warning descr="Unchecked cast: 'IDEA21547.A<capture<? extends IDEA21547.O>>' to 'IDEA21547.B<IDEA21547.O>'">(B<O>)a</warning>;
    System.out.println(b);
  }

  public void bar1(A<?> a, B<O> b) {
    b = <warning descr="Unchecked cast: 'IDEA21547.A<capture<?>>' to 'IDEA21547.B<IDEA21547.O>'">(B<O>)a</warning>;
    System.out.println(b);
  }

  public void bar2(A<?> a, B<?> b) {
    b = <warning descr="Unchecked cast: 'IDEA21547.A<capture<?>>' to 'IDEA21547.B<IDEA21547.O>'">(B<O>)a</warning>;
    System.out.println(b);
  }

  public void bar4(A<? extends O> a, B<?> b) {
    b = <warning descr="Unchecked cast: 'IDEA21547.A<capture<? extends IDEA21547.O>>' to 'IDEA21547.B<IDEA21547.O>'">(B<O>)a</warning>;
    System.out.println(b);
  }

  public void bar5(A<? super O> a, B<?> b) {
    b = <warning descr="Unchecked cast: 'IDEA21547.A<capture<? super IDEA21547.O>>' to 'IDEA21547.B<IDEA21547.O>'">(B<O>)a</warning>;
    System.out.println(b);
  }

  public void bar6(A a, B<?> b) {
    b = <warning descr="Unchecked cast: 'IDEA21547.A' to 'IDEA21547.B<IDEA21547.O>'">(B<O>)a</warning>;
    System.out.println(b);
  }
}
