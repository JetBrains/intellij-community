// "Replace lambda with method reference" "false"
import java.util.function.Function;
import java.util.function.Supplier;
class Foo {}
class Bar {}
class Something {
  static void stuff(Function<Foo, Bar> foo2bar) {}
  static void stuff(Supplier<Bar> sup4Bar) {}
}
class Something2 {
  static Bar bar(Foo foo) { return null; }
  static Bar bar() { return null; }
}
class Main {
  public static void main(String[] args) {
    Something.stuff(foo -> <caret>Something2.bar(foo));
    Something.stuff(() -> Something2.bar());
  }
}