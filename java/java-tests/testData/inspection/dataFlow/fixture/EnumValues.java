class EnumValues {
  void test() {
    X[] vals = X.values();
    vals[0] = null;
    foo();
    if(<warning descr="Condition 'vals[0] != null' is always 'false'">vals[0] != null</warning>) {}
  }

  native void foo();

  enum X {A, B, C}
}