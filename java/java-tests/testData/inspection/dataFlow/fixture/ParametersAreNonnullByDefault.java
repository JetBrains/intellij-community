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