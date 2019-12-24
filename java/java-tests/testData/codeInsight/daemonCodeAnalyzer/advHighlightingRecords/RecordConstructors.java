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
// Current spec draft allows this
record VarArgMismatch(int... x) {
  public VarArgMismatch(int[] x) {
    this.x = x;
  }
}
record VarArgMismatch2(int[] x) {
  public VarArgMismatch2(int... x) {
    this.x = x;
  }
}
record Delegate(int x) {
  public Delegate(int x) {
    <error descr="Canonical constructor cannot delegate to another constructor">this()</error>;
    this.x = 0;
  }
  
  public <error descr="Non-canonical record constructor must delegate to another constructor">Delegate</error>() {
  }
  
  public <error descr="Non-canonical record constructor must delegate to another constructor">Delegate</error>(int x, int y) {
    super();
  }
}
record NotInitializedField(int <error descr="Record component 'x' might not be initialized in canonical constructor">x</error>,
                           int <error descr="Record component 'y' might not be initialized in canonical constructor">y</error>,
                           int z) {
  public NotInitializedField(int x, int y, int z) {
    if (Math.random() > 0.5) this.y = y;
    this.z = z;
  }
}