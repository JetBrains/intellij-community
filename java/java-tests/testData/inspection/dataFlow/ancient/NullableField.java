class Test {
  public String s;

  public void foo() {
     s = null;
     boolean b = s.<warning descr="Method invocation 'equals' will produce 'NullPointerException'">equals</warning>(s);
  }
}