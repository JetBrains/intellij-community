import java.lang.annotation.*;

@Target({ElementType.TYPE_USE, ElementType.PARAMETER})
@interface NotNull {}

public class LocalClassImplicitParameters {

  private void foo(String foo, final Integer bar) {
    class Test {
      private Test(@NotNull String test) {
        if (test == null) {
          System.out.println(bar);
        }
      }
    }

    new Test(foo);
  }

  public int ok() {
    foo("a", 2);
    return 42;
  }

  public void failLocal() {
    foo(null, null);
  }

  public void failAnonymous() {
    new _Super("a") {
      void method(@NotNull java.util.List<?> test){}
    }.method(null);
  }

  public void failInner() {
    new Inner(null, "b");
  }

  private class Inner {
    Inner(@NotNull String param, @NotNull String param2) {
    }
  }
}

class _Super {
  _Super(@NotNull String f) {}
}