class A {
  void add(Object o, String param) {
    add(o, o != null ? null : (Object) param);
    add(o, o == null ? new Object() : (<warning descr="Casting 'param' to 'Object' is redundant">Object</warning>) param);
  }

  void add(Object o, Object param) {

  }
}