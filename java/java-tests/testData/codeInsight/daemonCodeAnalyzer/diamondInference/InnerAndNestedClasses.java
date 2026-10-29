import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.*;

@Target(ElementType.TYPE_USE)
@interface TA { }

class Outer<T> {
  class Inner<U> {
    Inner() { }
    Inner(U u) { }
    T outer() { return null; }
    U get() { return null; }
  }

  static class Nested<V> {
    Nested(V v) { }
    V get() { return null; }
  }

  void instanceContext() {
    Inner<String> i1 = new Inner<>();
    var i2 = new Inner<>(1);
    Integer x = i2.get();
    T t = i2.outer();
    Outer<T>.Inner<String> i3 = this.new Inner<>("a");
    Inner<Integer> i4 = new <error descr="Incompatible types. Found: 'Outer.Inner<java.lang.String>', required: 'Outer.Inner<java.lang.Integer>'">Inner<></error>("a");
  }

  static void staticContext() {
    new <error descr="'Outer.this' cannot be referenced from a static context">Inner<></error>();
    new Nested<>("a");
  }
}

class InnerAndNestedClasses {
  void qualified(Outer<String> outer, Outer<?> wildcard) {
    var b = outer.new Inner<>(1);
    String s = b.outer();
    Integer n = b.get();
    Outer<Integer>.Inner<Integer> bad = outer.new <error descr="Incompatible types. Found: 'Outer.Inner<java.lang.Integer>', required: 'Outer<java.lang.Integer>.Inner<java.lang.Integer>'">Inner<></error>(1);
    var f = new Outer<>().new Inner<>("x");
    Object fo = f.outer();
    var w = wildcard.new Inner<>("a");
    Object wo = w.outer();
    String ws = w.get();
  }

  void nested() {
    Outer.Nested<String> c = new Outer.Nested<>("a");
    var d = new Outer.Nested<>(1);
    Integer i = d.get();
    Outer.Nested<String> bad = new <error descr="Incompatible types. Found: 'Outer.Nested<java.lang.Integer>', required: 'Outer.Nested<java.lang.String>'">Outer.Nested<></error>(1);
  }

  void qualifiedNamesAndAnnotations() {
    java.util.List<String> fq = new java.util.ArrayList<>();
    List<String> annotated = new @TA ArrayList<>();
    Outer.Nested<String> annotatedNested = new Outer.@TA Nested<>("a");
  }

  void localClasses() {
    class Local<L> {
      Local(L l) { }
      L get() { return null; }
    }
    var l = new Local<>("a");
    String s = l.get();
    Local<Integer> bad = new <error descr="Incompatible types. Found: 'Local<java.lang.String>', required: 'Local<java.lang.Integer>'">Local<></error>("a");
  }
}
