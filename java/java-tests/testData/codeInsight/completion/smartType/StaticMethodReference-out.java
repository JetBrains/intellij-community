import java.util.stream.Stream;

class Example {
  private void example(Stream<String> s) {
    s.map(Example::method2)<caret>
  }

  private static String method2(String s) {
    return s;
  }
}