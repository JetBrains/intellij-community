import org.springframework.util.Assert;
import org.jetbrains.annotations.Nullable;

class Contracts {

  void foo(Object o) {
    Assert.isTrue(o instanceof String);
    String s = (String) o;
  }

  void foo1(Object o) {
    Assert.state(o instanceof String, "oops");
    String s = (String) o;
  }

  void foo2(@Nullable Object o) {
    Assert.notNull(o);
    System.out.println(o.hashCode());
  }

  void foo3(@Nullable Object o) {
    Assert.notNull(o, "not null");
    System.out.println(o.hashCode());
  }

}