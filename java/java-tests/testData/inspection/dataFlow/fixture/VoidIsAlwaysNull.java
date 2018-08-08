class VoidIsAlwaysNull {
  // IDEA-195506
  void foo(Void p) {
    System.out.println(p.<warning descr="Method invocation 'toString' may produce 'java.lang.NullPointerException'">toString</warning>());
  }
}