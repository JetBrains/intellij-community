import javax.annotation.ParametersAreNonnullByDefault;

class Test {
  static class XX {
    void get(Object t) {}
  }

  @ParametersAreNonnullByDefault
  static class X extends XX {
    // Do not warn as ParametersAreNonnullByDefault does not work for overridden parameters
    void get(Object x) {

    }
  }
}