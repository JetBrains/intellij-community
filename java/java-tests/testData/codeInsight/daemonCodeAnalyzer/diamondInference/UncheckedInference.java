import java.util.*;

class Wrapper<T> {
  Wrapper(List<T> list) { }
  Wrapper(T t, int i) { }
  T get() { return null; }
}

class UncheckedInference {
  void rawArguments(List raw, List<String> typed) {
    Wrapper<String> w1 = new Wrapper<>(typed);
    Wrapper<String> w2 = new <warning descr="Unchecked call to 'Wrapper(List<T>)' as a member of raw type 'Wrapper'"><warning descr="Unchecked assignment: 'Wrapper' to 'Wrapper<java.lang.String>'">Wrapper<></warning></warning>(raw);
    var w3 = new <warning descr="Unchecked call to 'Wrapper(List<T>)' as a member of raw type 'Wrapper'">Wrapper<></warning>(raw);
    Object o = w3.get();
    String s = w3.<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">get</error>();
    var w4 = new Wrapper<>(raw, 1);
    List l = w4.get();
    var w5 = new Wrapper<>(typed);
    String s2 = w5.get();
  }
}
