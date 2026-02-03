import org.jetbrains.annotations.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
  void method3(@Nullable MyEnum e) {
    if (MyEnum.foo == e) {
      System.out.println(e.hashCode());
    }
  }

  void test(List items) {
    MyEnum status = calcPodFileStatus();

    if (status == MyEnum.foo && items.isEmpty()) {
      return;
    }

    status.toString(); // false NPE warning here
  }

  @NotNull
  private static MyEnum calcPodFileStatus() {
    return MyEnum.foo;
  }
}
enum MyEnum { foo, bar }