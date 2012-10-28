import foo.NotNull;
import foo.Nullable;

import java.util.List;

class Test {
  private static void test(List<@NotNull Object> list) {
    if (<warning descr="Condition 'list.get(0) == null' is always 'false'">list.get(0) == null</warning>) {
      return;
    }
    list.add(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
  private static void test2(List<@Nullable Object> list) {
    if (list.get(0) == null) {
      return;
    }
    list.add(null);
  }

  private static void test3(Ref<@NotNull Object> ref) {
    if (ref.value == null) {
      return;
    }
    ref.value = null;
  }


}

class Ref<T> {
  T value;
}