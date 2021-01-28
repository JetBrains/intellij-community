class InstanceOfSubtype {
  void test(CharSequence cs) {
    if (cs instanceof String) {}
    if (cs instanceof String s) {}
    if (cs instanceof CharSequence) {}
    if (cs instanceof <error descr="Pattern type 'CharSequence' is the same as expression type">CharSequence</error> s) {}
    if (cs instanceof Object) {}
    if (cs instanceof <error descr="Pattern type 'Object' is a supertype of expression type 'CharSequence'">Object</error> s) {}
  }
}