record NotPublic(int x, int y) {
  <error descr="Canonical constructor must be 'public'">NotPublic</error>(int x, int y) {this.x = x; this.y = y;}
  NotPublic() {this(0,0);}
}
record Generic(String x) {
  public <error descr="Canonical constructor cannot have type parameters"><T></error> Generic(String x) {this.x = x;}
  public <T> Generic() {this("");}
}
record Throws() {
  public Throws() <error descr="Canonical constructor cannot declare thrown exceptions">throws Throwable</error> {}
  public Throws(int x) throws Throwable { this(); }
}
record TypeMismatch<T>(T t) {
  public TypeMismatch(<error descr="Incorrect parameter type for record component 't'. Expected: 'T', found: 'Object'">Object</error> t) {
    this.t = null;
  }
}
record Delegate(int x) {
  public Delegate(int x) {
    <error descr="Canonical constructor cannot delegate to another constructor">this()</error>;
  }
  
  public <error descr="Non-canonical record constructor must delegate to another constructor">Delegate</error>() {
  }
  
  public <error descr="Non-canonical record constructor must delegate to another constructor">Delegate</error>(int x, int y) {
    super();
  }
}