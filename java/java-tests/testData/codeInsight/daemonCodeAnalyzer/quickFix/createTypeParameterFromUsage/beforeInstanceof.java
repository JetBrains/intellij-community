// "Create type parameter 'Foo'" "false"

public class Instanceof {
  void test(Object o) {
    if (o instanceof Fo<caret>o) {}
  }
}