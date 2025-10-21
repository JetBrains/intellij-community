class ValidSubtype {

  public static void main(String[] args) {
    x(<warning descr="Redundant array creation for calling varargs method">new String[]</warning>{"firstly", "secondly", "finally"});
  }
  
  static void x(Object... os) {}
}