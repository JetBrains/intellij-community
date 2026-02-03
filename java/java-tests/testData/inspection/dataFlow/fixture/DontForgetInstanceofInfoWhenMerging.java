class Some {
  void foo() {
    Object o = null;
    while (equals(3)) {
      if (equals(2)) {
        if (o == null) {
          o = new String();
        }
      }
      if (<warning descr="Condition 'o == null || o instanceof String' is always 'true'">o == null || <warning descr="Condition 'o instanceof String' is always 'true' when reached">o instanceof String</warning></warning>) {
        System.out.println(o);
      }
    }
  }


}


