import javax.annotation.ParametersAreNonnullByDefault;

class Test {
  static class XX {
    void get(Object t) {}
  }

  @ParametersAreNonnullByDefault
  static class X extends XX {
    // Warn: parameter is effectively non-null via @ParametersAreNonnullByDefault
    void get(Object <warning descr="Parameter annotated @ParametersAreNonnullByDefault should not override non-annotated parameter">x</warning>) {

    }
  }
}