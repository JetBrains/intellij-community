// "Remove 'comparing()' call" "false"
import java.util.Comparator;
import java.util.function.Function;

public class MyFile {
  interface Foo extends Function<String, Integer>, Comparator<String> {}

  void test(Foo comparator) {
    // replacement will make the call ambiguous, thus not suggested
    Comparator<String> cmp = Comparator.comparing(String::length).thenComparing(Comparator.comp<caret>aring(comparator));
  }
}
