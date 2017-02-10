import java.util.stream.Stream;

abstract class ColonAutoComplete {
  void dv(Stream<Integer> stream, Checker checker) {
    stream.filter(checker:<caret>x)
  }
}

interface Checker {
  public boolean check(Integer s);
}