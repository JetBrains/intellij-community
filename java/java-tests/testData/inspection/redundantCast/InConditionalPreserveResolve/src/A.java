class A {
  void add(Object o, String param) {
    add(o, o != null ? null : (Object) param);
    add(o, o == null ? new Object() : (Object) param);
  }

  void add(Object o, Object param) {

  }
}