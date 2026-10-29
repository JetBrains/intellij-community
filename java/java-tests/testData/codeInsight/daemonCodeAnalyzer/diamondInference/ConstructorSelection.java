import java.io.IOException;

class GenericCtor<T> {
  <U> GenericCtor(T t, U u) { }
  T get() { return null; }
}

class BoundedCtor<T> {
  T t;
  <U extends T> BoundedCtor(U u) { }
}

class Overloads<T> {
  Overloads(T t) { }
  Overloads(String s) { }
  Overloads(T t, int i) { }
  Overloads(Object o, long l) { }
  T get() { return null; }
}

class Ambiguous<T> {
  Ambiguous(T t, String s) { }
  Ambiguous(String s, T t) { }
}

class Varargs<T> {
  @SafeVarargs
  Varargs(T... ts) { }
  T get() { return null; }
}

class Restricted<T> {
  private Restricted(T t) { }
  Restricted() { }
  static <T> Restricted<T> of(T t) { return new Restricted<>(t); }
}

class Throwing<T> {
  Throwing(T t) throws IOException { }
}

class NoArgs<T> {
  NoArgs() { }
}

class ConstructorSelection {
  void genericConstructors() {
    GenericCtor<String> g1 = new GenericCtor<>("a", 1);
    var g2 = new GenericCtor<>(1, "b");
    Integer i = g2.get();
    GenericCtor<String> bad = new <error descr="Incompatible types. Found: 'GenericCtor<java.lang.Integer>', required: 'GenericCtor<java.lang.String>'">GenericCtor<></error>(1, "b");
    GenericCtor<String> explicit = new <Integer>GenericCtor<error descr="Cannot use diamonds with explicit type parameters for constructor"><></error>("a", 1);
    GenericCtor<String> explicitNoDiamond = new <Integer>GenericCtor<String>("a", 1);
    BoundedCtor<Number> b1 = new BoundedCtor<>(1);
    var b2 = new BoundedCtor<>("s");
    String s = b2.t;
    BoundedCtor<String> b3 = new <error descr="Incompatible types. Found: 'BoundedCtor<java.lang.Integer>', required: 'BoundedCtor<java.lang.String>'">BoundedCtor<></error>(1);
  }

  void overloads() {
    var o1 = new Overloads<>("a");
    var o2 = new Overloads<>(1);
    Integer i = o2.get();
    Overloads<Integer> o3 = new Overloads<>("a");
    var o4 = new Overloads<>(1, 1);
    Integer i2 = o4.get();
    var o5 = new Overloads<>(1, 1L);
    new Ambiguous<><error descr="Cannot resolve constructor 'Ambiguous(String, String)'">("a", "b")</error>;
    new Ambiguous<>(1, "b");
    new Ambiguous<>("a", 1);
    new Ambiguous<><error descr="Cannot resolve constructor 'Ambiguous(int, int)'">(1, 1)</error>;
  }

  void varargs() {
    Varargs<String> v1 = new Varargs<>("a", "b");
    var v2 = new Varargs<>(1, 2);
    Integer i = v2.get();
    var v3 = new Varargs<>();
    Varargs<String> v4 = new Varargs<>();
    Varargs<String> v5 = new <error descr="Incompatible types. Found: 'Varargs<java.lang.Integer>', required: 'Varargs<java.lang.String>'">Varargs<></error>(1);
    Varargs<String> v6 = new Varargs<>(new String[0]);
  }

  void accessibilityAndExceptions() throws IOException {
    new <error descr="'Restricted(T)' has private access in 'Restricted'">Restricted</error><>("x");
    new Restricted<>();
    new Throwing<>("a");
    new NoArgs<><error descr="Expected no arguments but found 1">(1)</error>;
    new NoArgs<>();
  }
}
