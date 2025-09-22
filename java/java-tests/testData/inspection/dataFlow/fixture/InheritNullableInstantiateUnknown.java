import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class MyTest {
  @NotNullByDefault
  static class Iterables {
    public static native <T extends @Nullable Object> Iterable<T> concat(Iterable<? extends T> a, Iterable<? extends T> b);
  }

  void foo(List<String> l1, List<String> l2) {
    for (String s : Iterables.concat(l1, l2)) {
      System.out.println(s.length());
    }
  }
}