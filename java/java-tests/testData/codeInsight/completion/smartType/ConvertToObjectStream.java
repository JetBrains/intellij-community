import java.util.stream.Stream;

class Test {
  Stream<String> m(String[] a) {
    return a<caret>;
  }
}