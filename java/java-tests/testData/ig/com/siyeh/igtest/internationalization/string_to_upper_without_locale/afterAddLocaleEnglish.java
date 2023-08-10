import java.util.Locale;

// "Add 'Locale.ROOT' argument" "true"
class X {
  void test() {
    String foo = "bar";
    String foo1 = foo.toUpperCase(Locale.ROOT);
  }
}