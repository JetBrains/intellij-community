record NotPublic(int x, int y) {
  NotPublic(int x, int y) {this.x = x; this.y = y;}
  NotPublic() {this(0,0);}
}
record Generic(String x) {
  public <error descr="Canonical constructor cannot have type parameters"><T></error> Generic(String x) {this.x = x;}
  public <T> Generic() {this("");}
}
record Throws() {
  public Throws() <error descr="'throws' not allowed on canonical constructor">throws</error> Throwable {}
  public Throws(int x) throws Throwable { this(); }
}
record TypeMismatch<T>(T t) {
  public TypeMismatch(<error descr="Incorrect parameter type for record component 't'. Expected: 'T', found: 'Object'">Object</error> t) {
    this.t = null;
  }
}
record NameMismatch(int x, int y) {
  public NameMismatch(int <error descr="Canonical constructor parameter names must match record component names. Expected: 'x', found: '_x'">_x</error>, int <error descr="Canonical constructor parameter names must match record component names. Expected: 'y', found: '_y'">_y</error>) {
    x = _x;
    y = _y;
  }
}
// Current spec draft allows this
record VarArgMismatch(int... x) {
  public VarArgMismatch(<error descr="Incorrect parameter type for record component 'x'. Expected: 'int...', found: 'int[]'">int[]</error> x) {
    this.x = x;
  }
}
record VarArgMismatch2(int[] x) {
  public VarArgMismatch2(<error descr="Incorrect parameter type for record component 'x'. Expected: 'int[]', found: 'int...'">int...</error> x) {
    this.x = x;
  }
}
record Delegate(int x) {
  public Delegate(int x) {
    <error descr="Canonical constructor cannot delegate to another constructor">this()</error>;
    <error descr="Variable 'x' might already have been assigned to">this.x</error> = 0;
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
record ImplicitCanonicalConstructor(String s) {
  static void test() {
    new ImplicitCanonicalConstructor("Asdasd");
  }

  static class UsageInAnotherClass {
    static void test() {
      new ImplicitCanonicalConstructor<error descr="'ImplicitCanonicalConstructor(java.lang.String)' in 'ImplicitCanonicalConstructor' cannot be applied to '(int)'">(1)</error>;
    }
  }
}
record AssignmentInNonCanonical(int x, int y, long depth) {
  public AssignmentInNonCanonical(int x, int y) {
    this(x, y, 10);
    <error descr="Variable 'x' might already have been assigned to">this.x</error> = x;
  }

  void method() {
    <error descr="Cannot assign a value to final variable 'x'">this.x</error> = 0;
  }
}
record DelegateInitializesField(int n) {
  DelegateInitializesField(boolean b) {
    this(b ? 1 : 0);
    System.out.println(n);
  }
}
record BrokenRecord(int x, int y) {
  BrokenRecord(int x, int y) {
    this.x = x;
    this.y = y;
            <error descr="Unexpected token">.</error>
  }
}
class NonRecord {
  final int a;
  <error descr="Parameter list expected">NonRecord</error> {
    a = 10;
  }
}