package com.siyeh.igfixes.inheritance.problematic_varargs_method_override;

class One {

  public void m(String... ss) {}
}
class Two extends One {
  public void m(String... ss) {}
}
class Three {
  public static void main(String... args) {
    new Two().m("1", "2", "3");
  }
}