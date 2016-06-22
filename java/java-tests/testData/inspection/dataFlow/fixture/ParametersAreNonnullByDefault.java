import foo.*;

class Some {
  void foo() {
    NotNullClass.foo(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    NotNullClass.foo("a");
    
    NullableClass.foo(null);
    NullableClass.foo("a");
    
    AnotherPackageNotNull.foo(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    AnotherPackageNotNull.foo("a");
  }

}

@javax.annotation.ParametersAreNonnullByDefault
class CheckLoop {
  void foo(Iterable<String> it) {
    for (String s : it) {
      if (s == null) { // it's not ElementType.PARAMETER, no warning here
        System.out.println();
      }
    }
  }
}

@javax.annotation.ParametersAreNonnullByDefault
class NotNullClass {
  static void foo(String s) {}
  
}
@javax.annotation.ParametersAreNullableByDefault
class NullableClass {
  static void foo(String s) {}
}

@javax.annotation.ParametersAreNonnullByDefault
class Xxx {

  private int x;
  private String s;

  public Xxx(int x, String s) {
    this.x = x;
    this.s = s;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Xxx xxx = (Xxx) o;

    if (x != xxx.x) return false;
    return s != null ? s.equals(xxx.s) : xxx.s == null;

  }

}