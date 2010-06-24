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