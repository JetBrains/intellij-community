import java.lang.annotation.*;
import java.io.*;
import java.util.*;
import java.io.<error descr="Annotations are not allowed here">@SuppressWarnings</error> Reader;
import <error descr="Annotations are not allowed here">@SuppressWarnings</error> java.io.Writer;
import static java.lang.annotation.ElementType.*;

/*@Target({CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, PARAMETER, TYPE})*/ @interface A { }
@Target({TYPE_USE}) @interface TA { }
@Target({TYPE_PARAMETER}) @interface TPA { }

@A @TA <error descr="'@TPA' not applicable to type">@TPA</error>
class Outer {
  private Map<@TA String, @TA List<@TA <error descr="'@A' not applicable to type use">@A</error> String>> m;

  interface I { void m(int i); }
  private I i = (@TA <error descr="'@Override' not applicable to parameter">@Override</error> final int k) -> { };

  <error descr="'void' type may not be annotated">@TA</error> <T> void m(T t) { }
  <error descr="'void' type may not be annotated">@TA</error> void test1() {
    this.<@TA <error descr="'@TPA' not applicable to type use">@TPA</error> String>m("...");
  }

  class FF<F extends @TA String> { }

  Collection<? super @TA String> cs;

  interface BI<T> { }
  class BII<T> implements @TA BI<@TA T> { }
  class BIII extends @TA BII<Object> { }

  void tm() throws @TA RuntimeException { }

  class Middle {
    class Inner {
      void test() {
        @TA Inner v1;
        @TA Middle.@TA Inner v2;
        @TA Outer.@TA Middle.@TA Inner v3;
        @TA Outer v4;
        @TA Outer.@TA Middle v5;
        @TA Outer.@TA Middle.@TA Inner v6;
        List<@TA Outer.@TA Middle.@TA Inner> l;
      }
    }
  }

  static class StaticMiddle {
    static class StaticInner {
      void test() {
        @TA StaticInner v1;
        <error descr="Static member qualifying type may not be annotated">@TA</error> StaticMiddle.@TA StaticInner v2;
        <error descr="Static member qualifying type may not be annotated">@TA</error> Outer.<error descr="Static member qualifying type may not be annotated">@TA</error> StaticMiddle.@TA StaticInner v3;
        List<@TA Outer.<error descr="Static member qualifying type may not be annotated">@TA</error> StaticMiddle.@TA StaticInner> l;
      }
    }
  }

  {
    new @TA Object();
    new @TA ArrayList<String>();
    new @TA Runnable() { public void run() { } }.run();

    ArrayList<String> var = new <String> @TA ArrayList();
    new @TA Outer().new @TA Middle();

    @A Map.@TA Entry e1;
    @A <error descr="Static member qualifying type may not be annotated">@TA</error> Map.@TA Entry e2;
    @A java.<error descr="Annotation not applicable to this kind of reference">@TA</error> util.Map.@TA Entry e3;
    @A List<java.<error descr="Annotation not applicable to this kind of reference">@TA</error> lang.@TA String> l1;

    Object obj = "str";
    @TA String str = (@TA String)obj;

    boolean b = str instanceof @TA String;

    <error descr="Annotations are not allowed here">@TA</error> tm();

    try (@TA Reader r = new @TA FileReader("/dev/zero"); @TA Writer w = new @TA FileWriter("/dev/null")) { }
    catch (@TA IllegalArgumentException | @TA IOException e) { }

    @A @TA <error descr="Cannot resolve symbol 'Unknown'">Unknown</error>.@TA Unknown uu;

    Class<?> c1 = <error descr="Class literal type may not be annotated">@TA</error> String.class;
    Class<?> c2 = int <error descr="Class literal type may not be annotated">@TA</error> [].class;
  }

  interface IntFunction<T> { int apply(T t); }
  interface Sorter<T> { void sort(T[] a, Comparator<? super T> c); }
  void m1(IntFunction<Date> f) { }
  void m2(IntFunction<List<String>> f) { }
  void m3(Sorter<Integer> s) { }

  void lambdas() {
    m1(@TA Date::getDay);
    m1(<error descr="Annotations are not allowed here">@TA</error> java.util.@TA Date::getDay);
    m2(List<@TA String>::size);
    m3(Arrays::<@TA Integer>sort);

    Comparator<Object> cmp = (@TA Object x, @TA Object y) -> { System.out.println("x=" + x + " y=" + y); return 0; };
  }

  void m(List<@TA ? extends Comparable<Object>> p) { }

  void arrays(String @TA ... docs) {
    @TA String @TA [] @TA [] docs1 = new @TA String @TA [2] @TA [2];
    @TA int @TA [] ints = new @TA int @TA [2];
  }

  int @TA [] mixedArrays @TA [] <error descr="Annotations are not allowed here">@TA</error> = new int[0][0];
  int @TA [] mixedArrays(int @TA [] p @TA [] <error descr="Annotations are not allowed here">@TA</error>) @TA [] <error descr="Annotations are not allowed here">@TA</error> {
    int @TA [] a @TA [] <error descr="Annotations are not allowed here">@TA</error> = (p != null ? p : mixedArrays);
    return a;
  }
  void <error descr="Annotations are not allowed here">@TA</error> misplaced() { }

  @TA Outer() { }
  <T> <error descr="Annotations are not allowed here">@TA</error> Outer(T t) { }

  class MyClass<@TA @TPA T> { }
  interface MyInterface<@TA @TPA E> { }

  static class Super {
    protected int aField;
    int getField() { return aField; }
  }
  static class This extends Super {
    void superField() {
      IntFunction<Super> f = Outer.<error descr="Annotations are not allowed here">@TA</error> This.super::getField;
    }
  }
}
