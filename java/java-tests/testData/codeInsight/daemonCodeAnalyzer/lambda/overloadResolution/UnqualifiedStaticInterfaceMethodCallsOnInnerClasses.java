interface Test {

  final class Inner {

    void func() {
      of("");
      of();
      of("", "");
    }

  }

  static void of(String... lists) { }

}