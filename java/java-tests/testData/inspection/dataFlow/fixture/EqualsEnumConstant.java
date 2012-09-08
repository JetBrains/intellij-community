import org.jetbrains.annotations.*;
class TestIDEAWarn {
  void method(@Nullable MyEnum e) {
    if (e != MyEnum.foo) {return;}
    System.out.println(e.hashCode());
  }
  void method2(@Nullable MyEnum e) {
    if (e == MyEnum.foo) {
      System.out.println(e.hashCode());
    }
  }
}
enum MyEnum { foo, bar }