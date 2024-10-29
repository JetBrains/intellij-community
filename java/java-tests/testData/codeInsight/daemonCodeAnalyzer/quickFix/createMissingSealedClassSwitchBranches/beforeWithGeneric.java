// "Create missing branches 'Test.Bar' and 'Test.Foo'" "true-preview"
import java.util.List;

class Test {
  public static void main(String[] args) {
    List<Example<String, Integer>> examples = List.of();

    for (Example<String, Integer> example : examples) {
      String res = switch (example<caret>) {
      };
    }
  }

  interface AB<A, B> {
  }

  sealed interface Example<A, B> extends AB<A, B> permits Foo, Bar {
  }

  record Foo<A, B, C>(A a, C c) implements Example<A, B> {
  }

  static final class Bar<B> implements Example<String, B> {
  }

}