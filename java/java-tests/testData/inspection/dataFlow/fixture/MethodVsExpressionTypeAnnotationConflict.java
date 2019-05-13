import withTypeUse.NotNull;
import withTypeUse.Nullable;
import java.util.NavigableSet;

interface Foo<T> {
  @Nullable T get();
  T get2();
}

class Bar {

  public void test(Foo<@NotNull String> f, NavigableSet<@NotNull String> set) {
    if (f.get() == null) return;
    if (<warning descr="Condition 'f.get2() == null' is always 'false'">f.get2() == null</warning>) return;
    if (set.pollFirst() == null) return;
  }
}