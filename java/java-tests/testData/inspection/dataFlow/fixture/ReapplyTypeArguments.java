class InstanceOf {
  interface Super<T> {
    T get();
  }

  interface Sub extends Super<String> {}

  void test(Super<?> s) {
    if (s instanceof Sub) {
      if (<warning descr="Condition 's.get() instanceof String' is redundant and can be replaced with a null check">s.get() instanceof String</warning>) {

      }
    }
  }
}