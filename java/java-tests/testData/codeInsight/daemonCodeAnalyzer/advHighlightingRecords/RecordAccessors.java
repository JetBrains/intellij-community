record Rec(int x, int y) {
  public <error descr="Incorrect component accessor return type. Expected: 'int', found: 'void'">void</error> x() {}
  
  public void y(int x) {}
}
record RecTypeParam(int x) {
  public <error descr="Record component accessor cannot have type parameters"><T></error> int x() {return this.x;}
}
record RecStaticAccessor(int x) {
  public <error descr="Modifier 'static' not allowed here">static</error> int x() {return 0;}
}
record RecNonPublic(int x, int y, int z) {
  protected int <error descr="Record component accessor must be 'public'">x</error>() {return x;}
  int <error descr="Record component accessor must be 'public'">y</error>() {return y;}
  private int <error descr="Record component accessor must be 'public'">z</error>() {return z;}
}
record RecThrows(int x) {
  public int x() <error descr="Record component accessor cannot declare thrown exceptions">throws Exception</error> {return x;}
  public int y() throws Exception {return x;}
}
record CheckOverride(int x) {
  <error descr="Method does not override method from its superclass">@Override</error> public int x() { return x; }
  <error descr="Method does not override method from its superclass">@Override</error> public int y() { return x; }
}