class NotRecord {
  public <error descr="Parameter list expected">NotRecord</error> {
    
  }
}
record NonPublic(int x) {
  NonPublic {
    
  }
}
record Throws(int x) {
  public Throws <error descr="'throws' not allowed on compact constructor">throws</error> Throwable {}
}
record Generic() {
  public <error descr="Compact constructor cannot have type parameters"><T></error> Generic {}
}
record Delegate(int x) {
  public Delegate {
    <error descr="Canonical constructor cannot delegate to another constructor">this("")</error>;
  }
  <error descr="Non-canonical record constructor must delegate to another constructor">Delegate</error>(String s) {
    
  }
}
record ReturnInCompact(int x) {
  public ReturnInCompact {
    if (Math.random() > 0.5) <error descr="'return' statement is not allowed in compact constructor">return;</error>
  }
}
record NotInitialized(int x, 
                      int y, 
                      int z) {
  public NotInitialized {
    <error descr="Cannot assign a value to final variable 'x'">this.x</error> = 0;
    if (Math.random() > 0.5) <error descr="Cannot assign a value to final variable 'y'">this.y</error> = 1;
  }
}
record TwoCompacts(int x, int y) {
  <error descr="'TwoCompacts(int, int)' is already defined in 'TwoCompacts'">public TwoCompacts</error> {}
  <error descr="'TwoCompacts(int, int)' is already defined in 'TwoCompacts'">public TwoCompacts</error> {}
}
record CompactAndCanonical(int x, int y) {
  <error descr="'CompactAndCanonical(int, int)' is already defined in 'CompactAndCanonical'">public CompactAndCanonical(int x, int y)</error> {
    this.x = x;
    this.y = y;
  }
  <error descr="'CompactAndCanonical(int, int)' is already defined in 'CompactAndCanonical'">public CompactAndCanonical</error> {
    
  }
}
record WrittenFields(int x,
                     int y,
                     int z) {
public WrittenFields {
  <error descr="Cannot assign a value to final variable 'x'">this.x</error> = 0;
    if (Math.random() > 0.5) <error descr="Cannot assign a value to final variable 'y'">this.y</error> = 1;
  }
}
// IDEA-256804
record CompactCtorDelegate(String a) {
public CompactCtorDelegate {}
public CompactCtorDelegate() {
    this("hello");
    System.out.println(String.valueOf(this.a.charAt(0)));
  }
}