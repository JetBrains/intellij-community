import typeUse.*;
import java.util.*;

class MyNonNullGenericClass<T extends @NotNull Object> {
  public static void test() {
    MyNonNullGenericClass<<warning descr="Non-null type argument is expected">@Nullable String</warning>> foo = new MyNonNullGenericClass<>();
  }

}


class Parent<A extends @Nullable Object, B extends @NotNull Object> { }
class Child<A, B> extends Parent<B, <warning descr="Non-null type argument is expected">A</warning>> {
  void test2() {
    Parent<@Nullable String, <warning descr="Non-null type argument is expected">@Nullable String</warning>> p = new Parent<>();
    Child<@Nullable String, @Nullable String> c = new Child<>();
  }
}