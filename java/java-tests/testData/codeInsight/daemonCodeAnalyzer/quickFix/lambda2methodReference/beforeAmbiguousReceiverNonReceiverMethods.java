// "Replace lambda with method reference" "false"
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
class Foo {
  public String bar() {
    return "foo";
  }

  public static String bar(Foo foo) {
    return "bar";
  }
}

class Test {
  public void test() {
    Stream.of(new Foo()).map(e -> e.b<caret>ar());
  }
}
