import java.util.*;
import java.util.Comparator;


/**
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Mar 25, 2004
 * Time: 8:08:44 PM
 * To change this template use File | Settings | File Templates.
 */
class VarianceTesting {
    void method(List<? extends VarianceTesting> l) {
//        l.add(new VarianceTesting());
        l.add(null);
    }

    static void shuffle(Collection<?> c) {}

    static class X<T> {
        T field;
        T[] arrayField;
        T[] method() {return  arrayField;};
        void putAll(Collection<? super T> c) {}
    }

    void method1(List<? super VarianceTesting> l) {
        List<? extends VarianceTesting> l1 = new ArrayList<VarianceTesting>();
        l1.add<error descr="'add(capture<? extends VarianceTesting>)' in 'java.util.List' cannot be applied to '(VarianceTesting)'">(new VarianceTesting())</error>;
        List<List<? extends VarianceTesting>> lll = null;
        lll.add(l1);
        X<? extends VarianceTesting> x = new X<VarianceTesting>();
        VarianceTesting z = x.field;
        VarianceTesting[] v = x.arrayField;
        VarianceTesting v1 = x.arrayField[0];
        x.arrayField[0] = new VarianceTesting();
        <error descr="Incompatible types. Found: 'VarianceTesting', required: 'capture<? extends VarianceTesting>'">x.field = new VarianceTesting()</error>;
        VarianceTesting[] k = x.method();
        k[0] = new VarianceTesting();
        x.method()[0] = new VarianceTesting();
        <error descr="Incompatible types. Found: 'VarianceTesting[]', required: 'capture<? extends VarianceTesting>[]'">x.arrayField = new VarianceTesting[10]</error>;
        l1.addAll<error descr="'addAll(java.util.Collection<? extends capture<? extends VarianceTesting>>)' in 'java.util.List' cannot be applied to '(java.util.ArrayList<VarianceTesting>)'">(new ArrayList<VarianceTesting>())</error>;
        <error descr="Incompatible types. Found: 'java.util.ArrayList<java.lang.String>', required: 'java.util.List<? extends VarianceTesting>'">List<? extends VarianceTesting> l2 = new ArrayList<String>();</error>
        List<? extends VarianceTesting> l3 = l2;
        VarianceTesting t = l1.get(0);
        l.add(new VarianceTesting());
        l.add(null);
        <error descr="Incompatible types. Found: 'java.lang.Object', required: 'VarianceTesting'">VarianceTesting t1 = l.get(0);</error>
        X<? extends VarianceTesting> x1 = null;
        x1.putAll(new ArrayList<VarianceTesting>());
        List<?> unknownlist = l;
        List<?> unknownlist1 = new ArrayList<VarianceTesting>();
        List<?> unknownlist2 = new ArrayList<<error descr="Wildcard type '?' cannot be instantiated directly">?</error>>();
        shuffle(l);
        shuffle(new ArrayList<VarianceTesting>());
        List<VarianceTesting> lllll = new ArrayList<VarianceTesting>();
        lllll.removeAll(new ArrayList<String>());
    }

}

class SuperTester <U> {
     void go(Acceptor<? super U> acceptor, U u) {
          acceptor.accept(<error descr="'accept(SuperTester<capture<? super U>>, capture<? super U>)' in 'SuperTester.Acceptor' cannot be applied to '(SuperTester<U>, U)'">this</error>, u);
     }

     static class Acceptor <V> {
          void accept(SuperTester<V> tester, V v) { }
     }
}

class SCR40202 {
    void foo(Map<?, String> map) {
        for (<error descr="Incompatible types. Found: 'java.util.Iterator<java.util.Map.Entry<capture<?>,java.lang.String>>', required: 'java.util.Iterator<java.util.Map.Entry<?,java.lang.String>>'">Iterator<Map.Entry<?, String>> it = map.entrySet().iterator();</error> it.hasNext();) {

        }
    }
}

class CaptureTest {
   static class Emum<T> {
      T t;
      public static <T extends Emum<T>> T valueOf(Class<T> enumType,
                                                String name) {
                                                return null;
      }
   }

   void foo (Class<? extends Emum<CaptureTest>> clazz) {
     <error descr="Inferred type 'capture<? extends CaptureTest.Emum<CaptureTest>>' for type parameter 'T' is not within its bound; should extend 'CaptureTest.Emum<capture<? extends CaptureTest.Emum<CaptureTest>>>'">Emum.valueOf(clazz, "CCC")</error>;
   }
}

class SuperTest {
    public List<List<? extends SuperTest>> waitingList;

    public Comparator<List<?>> SIZE_COMPARATOR;

    {
       //This call has its type arguments inferred alright: T -> List<capture<? extends SuperTest>>
       Collections.sort(waitingList, SIZE_COMPARATOR);
    }
}

class Bug<A> {
    static class B<C> {
    }

    static class D<E> {
        B<E> f() {
            return null;
        }
    }

    <G extends A> void h(B<G> b) {
    }
    
    void foo(D<? extends A> d) {
        h(d.f());   //This call is OK as a result of reopening captured wildcard for calling "h"
    }
}

//IDEA-4215
class Case2 {
        class A {}

        class B extends A {}

        Comparator<A> aComparator;
        Case2() {

            ArrayList<B> blist = new ArrayList<B>();

            // this call is OK: T -> B
            Collections.sort(blist, aComparator);
        }
}

class S1 {
    <T> void f(List<T> l1, T l2) {

    }

    void bar(List<? extends S1> k) {
        f(k,  <error descr="'f(java.util.List<capture<? extends S1>>, capture<? extends S1>)' in 'S1' cannot be applied to '(java.util.List<capture<? extends S1>>, S1)'">k.get(0)</error>);
    }
}

class S2 {
    <T> void f(List<T> l1, List<T> l2) {

    }

    void bar(List<? extends S2> k) {
        f(k, <error descr="'f(java.util.List<capture<? extends S2>>, java.util.List<capture<? extends S2>>)' in 'S2' cannot be applied to '(java.util.List<capture<? extends S2>>, java.util.List<capture<? extends S2>>)'">k</error>);
    }
}

class S3 {
    <T> void f(Map<T,T> l2) {

    }

    void bar(Map<? extends S3, ? extends S3> k) {
        f<error descr="'f(java.util.Map<capture<? extends S3>,capture<? extends S3>>)' in 'S3' cannot be applied to '(java.util.Map<capture<? extends S3>,capture<? extends S3>>)'">(k)</error>;
    }
}

class TypeBug {
    private static class ValueHolder<T> {
        public T value;
    }

    public static void main(final String[] args) {
        List<ValueHolder<?>> multiList = new ArrayList<ValueHolder<?>>();

        ValueHolder<Integer> intHolder = new ValueHolder<Integer>();
        intHolder.value = 1;

        ValueHolder<Double> doubleHolder = new ValueHolder<Double>();
        doubleHolder.value = 1.5;

        multiList.add(intHolder);
        multiList.add(doubleHolder);
        swapFirstTwoValues<error descr="'swapFirstTwoValues(java.util.List<TypeBug.ValueHolder<T>>)' in 'TypeBug' cannot be applied to '(java.util.List<TypeBug.ValueHolder<?>>)'">(multiList)</error>; //need to be highlighted

        // this line causes a ClassCastException when checked.
        Integer value = intHolder.value;
        System.out.println(value);
    }

    private static <T> void swapFirstTwoValues(List<ValueHolder<T>> multiList) {
        ValueHolder<T> intHolder = multiList.get(0);
        ValueHolder<T> doubleHolder = multiList.get(1);

        intHolder.value = doubleHolder.value;
    }
}

class OtherBug {
public static void foo(List<? extends Foo> foos) {
    final Comparator<Foo> comparator = createComparator();
    Collections.sort(foos, comparator);  //this call is OK
  }

  private static Comparator<Foo> createComparator() {
    return null;
  }

  public interface Foo {
  }
}

class OtherBug1 {
  public static void foo(List<? super Foo> foos) {
    final Comparator<Foo> comparator = createComparator();
    Collections.sort(foos, <error descr="'sort(java.util.List<capture<? super OtherBug1.Foo>>, java.util.Comparator<? super capture<? super OtherBug1.Foo>>)' in 'java.util.Collections' cannot be applied to '(java.util.List<capture<? super OtherBug1.Foo>>, java.util.Comparator<OtherBug1.Foo>)'">comparator</error>);
  }

  private static Comparator<Foo> createComparator() {
    return null;
  }

  public interface Foo {
  }
}

//IDEADEV-7187
class AA <B extends AA<B,C>, C extends AA<C, ?>>{}
//end of IDEADEV-7187

//IDEADEV-8697
class GenericTest99<E extends GenericTest99<E, F>,F> {
}
class GenericTest99D<E extends GenericTest99D<E>> extends GenericTest99<E,Double> {
}
class Use99<U extends GenericTest99<?,F>,F> {
}
class Use99n extends Use99<GenericTest99D<?>,Double> {
}
//end of IDEADEV-8697

class IDEA79360 {
    public static void main(Map<?, ?> map, Map<Object, Object> test) {
        map.putAll<error descr="'putAll(java.util.Map<? extends capture<?>,? extends capture<?>>)' in 'java.util.Map' cannot be applied to '(java.util.Map<java.lang.Object,java.lang.Object>)'">(test)</error>;
        map.put<error descr="'put(capture<?>, capture<?>)' in 'java.util.Map' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        map.put<error descr="'put(capture<?>, capture<?>)' in 'java.util.Map' cannot be applied to '(java.lang.Object, java.lang.Object)'">(new Object(), new Object())</error>;
        map = new HashMap<Object, Object>(test);
    }
}

class GenericFailureExample {

 interface Descriptor<T extends Comparable<T>> {
   Class<T> getType();
 }

 void isMarkedFaultyButCompilesClean(Descriptor<?> n) {
   bar(n.getType());
 }

 <T extends Comparable<T>> void butThisWorks(Descriptor<T> n) {
   bar(n.getType());
 }

 <T extends Comparable<T>> Comparator<T> bar(Class<T> type) {
   return null;
 }
}

//IDEA-67675
abstract class A67675<T>
{
    abstract T foo();
}

abstract class B67675<T> extends A67675<T[]> { }

class C67675<T extends B67675<?>>
{
    void foo(T x)
    {
        x.foo()[0] = "";
    }
}