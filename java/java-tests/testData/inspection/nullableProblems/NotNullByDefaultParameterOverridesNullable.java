import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;

class Test {
  static class XX {
    void get(@Nullable Object t) {}
  }

  @ParametersAreNonnullByDefault
  static class X extends XX {
    void get(Object <warning descr="Parameter annotated @ParametersAreNonnullByDefault must not override @Nullable parameter">x</warning>) {

    }
  }
}