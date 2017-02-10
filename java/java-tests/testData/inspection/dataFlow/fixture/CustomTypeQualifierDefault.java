import foo.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Some {
  void foo(@NotNull String s) {
    NotNullClass.foo(null);
    if (<warning descr="Condition 'NotNullClass.foo(s) == null' is always 'false'">NotNullClass.foo(s) == null</warning>) {}
    
    NullableClass.foo(null);
    if (NullableClass.foo("a") == null) {}
    
    AnotherPackageNotNull.foo(null);
    if (<warning descr="Condition 'AnotherPackageNotNull.foo(s) == null' is always 'false'">AnotherPackageNotNull.foo(s) == null</warning>) {}
  }

}

@bar.MethodsAreNotNullByDefault
class NotNullClass {
  static native Object foo(String s);
  
  public Object foo() {
    return <warning descr="'null' is returned by the method declared as @MethodsAreNotNullByDefault">null</warning>;
  }

  private String privateFoo() {
    return <warning descr="'null' is returned by the method declared as @MethodsAreNotNullByDefault">null</warning>;
  }

  {
    String s2 = privateFoo();
    int i2 = s2.length();
  }

  @Nullable
  public Object foo2() {
    return null;
  }
  

}
class NullableClass {
  static native Object foo(String s);
}