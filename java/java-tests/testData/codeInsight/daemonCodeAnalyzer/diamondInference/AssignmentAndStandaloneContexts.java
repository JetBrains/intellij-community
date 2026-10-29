import java.util.*;

class Box<T> {
  T value;
  Box() { }
  Box(T value) { this.value = value; }
  T get() { return value; }
  Box<T> copy() { return new Box<>(value); }
}

class Pair<A, B> {
  Pair(A a, B b) { }
  A first() { return null; }
  B second() { return null; }
}

class NumBox<N extends Number> {
  NumBox() { }
  NumBox(N n) { }
  N get() { return null; }
}

class SelfBounded<S extends Comparable<S>> {
  S get() { return null; }
}

class AssignmentAndStandaloneContexts {
  List<String> field = new ArrayList<>();
  Map<String, List<Integer>> map = new HashMap<>();
  static final Box<String> CONSTANT = new Box<>("c");
  Box<Integer> badField = new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<java.lang.Integer>'">Box<></error>("c");

  void assignmentContext() {
    List<String> list = new ArrayList<>();
    Collection<String> coll = new ArrayList<>();
    Box<String> b1 = new Box<>();
    Box<String> b2 = new Box<>("a");
    Box<Integer> b3 = new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<java.lang.Integer>'">Box<></error>("a");
    Box<Object> b4 = new Box<>("a");
    Box<CharSequence> b5 = new Box<>("a");
    Box<? extends Number> b6 = new Box<>(1);
    Box<? super Integer> b7 = new Box<>(1);
    Box<? extends CharSequence> b8 = new <error descr="Incompatible types. Found: 'Box<java.lang.Integer>', required: 'Box<? extends java.lang.CharSequence>'">Box<></error>(1);
    Box<?> b9 = new Box<>();
    Box<Box<String>> nested = new Box<>(new Box<>("a"));
    Box<Box<String>> nestedBad = new <error descr="Incompatible types. Found: 'Box<Box<java.lang.Integer>>', required: 'Box<Box<java.lang.String>>'">Box<></error>(new Box<>(1));
    Pair<String, Integer> p1 = new Pair<>("a", 1);
    Pair<String, Integer> p2 = new <error descr="Incompatible types. Found: 'Pair<java.lang.Integer,java.lang.String>', required: 'Pair<java.lang.String,java.lang.Integer>'">Pair<></error>(1, "a");
    Pair<Number, Object> p3 = new Pair<>(1, "a");
    Object o = new Box<>("a");
    list = new LinkedList<>();
    b1 = new Box<>(null);
    <error descr="Incompatible types. Found: 'Box<java.lang.Integer>', required: 'Box<java.lang.String>'">b2 = new Box<>(1)</error>;
    Box<int[]> arr = new Box<>(new int[0]);
    Box<long[]> arrBad = new <error descr="Incompatible types. Found: 'Box<int[]>', required: 'Box<long[]>'">Box<></error>(new int[0]);
    Box<Long> unboxedArgument = new <error descr="Incompatible types. Found: 'Box<java.lang.Integer>', required: 'Box<java.lang.Long>'">Box<></error>(1);
  }

  void standaloneContext() {
    var b = new Box<>("a");
    String s = b.get();
    var empty = new Box<>();
    String s2 = empty.<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">get</error>();
    Object o = empty.get();
    var pair = new Pair<>(1, 2L);
    int i = pair.first();
    long l = pair.second();
    String bad = pair.<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.String'">first</error>();
    var list = new ArrayList<>();
    list.add("x");
    list.add(1);
    new Box<>("a").get().length();
    new Box<>().get().<error descr="Cannot resolve method 'length' in 'Object'">length</error>();
    int len = new Box<>("abc").get().length();
    List<String> sub = new ArrayList<>().<error descr="Incompatible types. Found: 'java.util.List<java.lang.Object>', required: 'java.util.List<java.lang.String>'">subList</error>(0, 0);
    List<String> sub2 = new ArrayList<String>().subList(0, 0);
    String concat = "" + new Box<>("a");
    var nestedVar = new Box<>(new Box<>("a"));
    String deep = nestedVar.get().get();
  }

  void boundedTypeParameters() {
    var nb = new NumBox<>();
    Number n = nb.get();
    Integer bad = nb.<error descr="Incompatible types. Found: 'java.lang.Number', required: 'java.lang.Integer'">get</error>();
    NumBox<Integer> ni = new NumBox<>();
    var nd = new NumBox<>(1.5);
    Double d = nd.get();
    new NumBox<><error descr="'NumBox(N)' in 'NumBox' cannot be applied to '(java.lang.String)'">("a")</error>;
    NumBox<? extends Integer> wi = new NumBox<>();
    var sb = new SelfBounded<>();
    Comparable<?> c = sb.get();
    SelfBounded<String> ss = new SelfBounded<>();
  }

  void castContext() {
    List<String> l1 = <error descr="Inconvertible types; cannot cast 'java.util.ArrayList<java.lang.Object>' to 'java.util.List<java.lang.String>'">(List<String>) new ArrayList<>()</error>;
    Object o = (Object) new ArrayList<>();
    List<?> l2 = (List<?>) new ArrayList<>();
    String s = ((Box<String>) new Box<>("a")).get();
    Integer i = (<error descr="Inconvertible types; cannot cast 'Box<java.lang.String>' to 'Box<java.lang.Integer>'">(Box<Integer>) new Box<>("a")</error>).get();
  }

  Box<String> returnContext(boolean flag) {
    if (flag) return new Box<>();
    return new Box<>("x");
  }

  Box<Integer> badReturnContext() {
    return new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<java.lang.Integer>'">Box<></error>("x");
  }

  void arrayInitializer() {
    Object[] objects = { new Box<>("a"), new ArrayList<>() };
    Box<?>[] boxes = { new Box<>("a"), new Box<>() };
  }

  <T> Box<T> typeVariableArgument(T t) {
    Box<T> b = new Box<>(t);
    Box<String> bad = new <error descr="Incompatible types. Found: 'Box<T>', required: 'Box<java.lang.String>'">Box<></error>(t);
    return new Box<>(b.get());
  }

  void whitespace() {
    List<String> list = new ArrayList< >();
    List<String> list2 = new ArrayList</* comment */>();
  }
}
