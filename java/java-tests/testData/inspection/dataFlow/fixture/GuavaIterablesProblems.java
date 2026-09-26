import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class MyTest {
  @NotNullByDefault
  static class Iterables {
    public static native <T extends @Nullable Object> Iterable<T> concat(Iterable<? extends T> a, Iterable<? extends T> b);

    public static native <T extends @Nullable Object> T[] toArray(
      Iterable<? extends T> iterable, Class<@NotNull T> type);

    public static native <T extends @Nullable Object> T get(
      Iterable<T> iterable, int position);
  }

  void foo1(List<@Nullable Integer> l1, List<@Nullable String> l2) {
    for (Object s : Iterables.concat(l1, l2)) {
      System.out.println(s.<warning descr="Method invocation 'toString' may produce 'NullPointerException'">toString</warning>());
    }
  }


  void foo2(List<@Nullable Integer> l1, List<String> l2) {
    for (Object s : Iterables.concat(l1, l2)) {
      System.out.println(s.<warning descr="Method invocation 'toString' may produce 'NullPointerException'">toString</warning>());
    }
  }
  
  void toArray(List<@Nullable String> l1) {
    for (String s : Iterables.toArray(l1, String.class)) {
      System.out.println(s.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());

    }
  }

  void get(List<@Nullable String> l1) {
    System.out.println(Iterables.get(l1, 0).<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());

  }

  void get2(List<String> l1) {
    System.out.println(Iterables.get(l1, 0).length());
  }

  void foo(List<String> l1, List<String> l2) {
    for (String s : Iterables.concat(l1, l2)) {
      System.out.println(s.length());
    }
  }
}