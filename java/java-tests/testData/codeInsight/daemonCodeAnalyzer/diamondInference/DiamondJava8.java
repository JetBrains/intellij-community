import java.util.*;

class Box<T> {
  Box() { }
  Box(T t) { }
  T get() { return null; }
}

class DiamondJava8 {
  List<String> field = new ArrayList<>();

  static void takeStrings(List<String> l) { }
  static void takeObjects(List<Object> l) { }
  static <T> List<T> wrap(Box<T> b) { return null; }
  static <T> T id(T t) { return t; }

  List<String> returnContext() {
    return new ArrayList<>();
  }

  void test(boolean cond) {
    List<String> list = new ArrayList<>();
    Box<String> box = new Box<>("a");
    Box<Integer> bad = new <error descr="Incompatible types. Found: 'Box<java.lang.String>', required: 'Box<java.lang.Integer>'">Box<></error>("a");
    Box<? extends Number> wildcard = new Box<>(1);
    int len = new Box<>("abc").get().length();
    takeStrings(new ArrayList<>());
    takeObjects(new ArrayList<>());
    List<String> wrapped = wrap(new Box<>());
    List<String> wrappedArg = wrap(new Box<>("a"));
    List<String> identity = id(new ArrayList<>());
    List<String> conditional = cond ? new ArrayList<>() : new LinkedList<>();
    Box<String> anonymousBox = new Box<error descr="Cannot use '<>' with anonymous inner classes"><></error>() { };
    Comparator<String> anonymous = new <error descr="Class 'Anonymous class derived from Comparator' must implement abstract method 'compare(T, T)' in 'Comparator'">Comparator<></error>() {
      public int compare(String a, String b) { return 0; }
    };
  }
}
