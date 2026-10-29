import java.util.*;
import java.util.function.*;

class Box<T> {
  Box() { }
  Box(T t) { }
  T get() { return null; }
}

class InvocationContext {
  static void takeStrings(List<String> l) { }
  static void takeIntBox(Box<Integer> b) { }
  static void takeWildcard(Box<? extends Number> b) { }
  static <T> T id(T t) { return t; }
  static <T> List<T> wrap(Box<T> b) { return null; }
  static <T> void same(Box<T> a, Box<T> b) { }
  static <T extends Number> T numeric(Box<T> b) { return null; }
  static <T> T unwrap(Box<? extends T> b) { return null; }

  static void over(List<String> l) { }
  static void over(Set<String> s) { }
  static void ambiguous(List<String> l) { }
  static void ambiguous(Collection<Integer> c) { }
  static void specific(Collection<String> c) { }
  static void specific(ArrayList<String> c) { }
  static void boxed(Box<Integer> b, int x) { }
  @SafeVarargs
  static void varargs(Box<String>... boxes) { }

  void methodArguments() {
    takeStrings(new ArrayList<>());
    takeIntBox(new Box<>());
    takeIntBox(new Box<>(1));
    takeIntBox(new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<java.lang.Integer>'">Box<></error>("a"));
    takeWildcard(new Box<>());
    takeWildcard(new Box<>(1L));
    takeWildcard(new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<? extends java.lang.Number>'">Box<></error>("a"));
    List<String> l1 = id(new ArrayList<>());
    List<String> l2 = wrap(new Box<>());
    List<Integer> l3 = <error descr="Incompatible types. Found: 'java.util.List<java.lang.String>', required: 'java.util.List<java.lang.Integer>'">wrap</error>(new Box<>("a"));
    same(new Box<>("a"), new Box<>(1));
    same(new Box<>(), new Box<String>());
    Integer n1 = numeric(new Box<>(1));
    Integer n2 = numeric(new Box<>());
    numeric<error descr="'numeric(Box<T>)' in 'InvocationContext' cannot be applied to '(Box<java.lang.String>)'">(new Box<>("a"))</error>;
    String u = unwrap(new Box<>("a"));
    Integer uBad = <error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.Integer'">unwrap</error>(new Box<>("a"));
    List<String> explicit = InvocationContext.<List<String>>id(new ArrayList<>());
    List<String> explicitBad = InvocationContext.<List<Integer>><error descr="Incompatible types. Found: 'java.util.List<java.lang.Integer>', required: 'java.util.List<java.lang.String>'">id</error>(new ArrayList<>());
    Box<String> nestedCalls = id(id(new Box<>()));
    List<List<String>> lists = Arrays.asList(new ArrayList<>(), new LinkedList<>());
  }

  void overloadResolution() {
    over(new ArrayList<>());
    over(new HashSet<>());
    ambiguous<error descr="Ambiguous method call: both 'InvocationContext.ambiguous(List<String>)' and 'InvocationContext.ambiguous(Collection<Integer>)' match">(new ArrayList<>())</error>;
    specific(new ArrayList<>());
    boxed(new Box<>(), 1);
    varargs(new Box<>(), new Box<>("a"));
    varargs<error descr="Cannot resolve method 'varargs(Box<T>)'">(new Box<>(1))</error>;
  }

  void conditionalAndSwitch(boolean cond, int k) {
    List<String> c1 = cond ? new ArrayList<>() : new LinkedList<>();
    List<String> c2 = cond ? new ArrayList<>() : null;
    List<String> c3 = cond ? new ArrayList<>() : new <error descr="Incompatible types. Found: 'java.util.ArrayList<java.lang.Integer>', required: 'java.util.List<java.lang.String>'">ArrayList<></error>(Arrays.asList(1));
    takeStrings(cond ? new ArrayList<>() : new LinkedList<>());
    Object o = cond ? new Box<>("a") : new Box<>(1);
    var v = cond ? new Box<>("a") : new Box<>(1);
    List<String> sw = switch (k) {
      case 0 -> new ArrayList<>();
      case 1 -> {
        yield new LinkedList<>();
      }
      default -> new Vector<>();
    };
    takeIntBox(switch (k) {
      case 0 -> new Box<>();
      default -> new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<java.lang.Integer>'">Box<></error>("a");
    });
  }

  void lambdasAndMethodReferences() {
    Supplier<List<String>> s1 = () -> new ArrayList<>();
    Supplier<List<String>> s2 = () -> {
      return new ArrayList<>();
    };
    Function<String, Box<String>> f1 = x -> new Box<>(x);
    Function<String, Box<Integer>> f2 = x -> new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<java.lang.Integer>'">Box<></error>(x);
    Supplier<Box<String>> ref = Box::new;
    Function<Integer, Box<Integer>> ref2 = Box::new;
    Runnable r = () -> new Box<>("a");
    Map<String, List<Integer>> m = new HashMap<>();
    m.put("a", new ArrayList<>());
    m.computeIfAbsent("b", key -> new ArrayList<>()).add(1);
    m.computeIfAbsent("c", key -> new ArrayList<>()).add<error descr="'add(java.lang.Integer)' in 'java.util.List' cannot be applied to '(java.lang.String)'">("x")</error>;
  }
}
