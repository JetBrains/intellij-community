import org.jetbrains.annotations.*;

class Test {
  <warning descr="Not-null fields must be initialized">@NotNull</warning> Object member;
  <warning descr="Not-null fields must be initialized">@NotNull</warning> Object member2;

  public Test(Object p) {
    I i = param -> {
      member = param;
    };
    I i2 = new I() {
      @Override
      public void foo(Object param) {
        member2 = param;
      }
    };
    i.foo(p);
    i2.foo(p);
  }
}

interface I {
  void foo(Object param);
}