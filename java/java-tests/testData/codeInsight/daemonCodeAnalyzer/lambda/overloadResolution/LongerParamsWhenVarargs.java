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