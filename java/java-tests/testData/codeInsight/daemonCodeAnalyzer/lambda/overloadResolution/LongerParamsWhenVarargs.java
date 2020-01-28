interface Node<<warning descr="Type parameter 'T' is never used">T</warning>> {

  @SafeVarargs
  static <T> Node<T> of(T value, Node<T>... children) {
    System.out.println(value);
    System.out.println(children);
    return null;
  }


  @SafeVarargs
  static <T1> Node<T1> of(T1... values) {
    System.out.println(values);
    return null;
  }

  static void test() {
    Node.of(1, Node.of(2), Node.of(3));
    Node.<Integer>of(1, Node.<Integer>of(2), Node.<Integer> of(3));
  }
}

class MyTest {
      void foo(String... s) {
        System.out.println(s);
      }

  <T> void foo(T t, String t3, T... s) {
    System.out.println(t);
    System.out.println(t3);
    System.out.println(s);
  }

  {
    foo(" ", " ", "");
  }
}