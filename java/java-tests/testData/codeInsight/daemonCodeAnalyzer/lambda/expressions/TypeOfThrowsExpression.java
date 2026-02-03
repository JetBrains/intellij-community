import java.util.function.Function;

class SomeClass implements IdeaBloopers11 {
  void foo(Function<String, String> f) {}

  void bar() {
    fo<caret>o(s -> {
      if (s.contains("a")) {
        throw baz(s);
      }
      return s;
    });
  }

  private NullPointerException baz(String s) {
    return null;
  }
}
