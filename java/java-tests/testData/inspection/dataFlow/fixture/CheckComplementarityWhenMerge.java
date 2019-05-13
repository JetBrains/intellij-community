import org.jetbrains.annotations.*;

import java.util.Collection;

class NullableTest {

  public void foo(@Nullable String a, Collection<D> test) {
    boolean filterA = a == null;
    for (D i : test) {
      if ("c".equals(i.getE()) || filterA || bar(a)) {
        System.out.println("ok");
      }
    }
  }

  private native boolean bar(@NotNull String c);

  private interface D {
    String getE();
  }

}