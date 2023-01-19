class InstanceOfSubtypeJava19 {
  void test(CharSequence cs) {
    if (cs instanceof String) {}
    if (cs instanceof String s) {}
    if (cs instanceof CharSequence) {}
    if (cs instanceof CharSequence s) {}
    if (cs instanceof Object) {}
    if (cs instanceof Object s) {}
  }
}