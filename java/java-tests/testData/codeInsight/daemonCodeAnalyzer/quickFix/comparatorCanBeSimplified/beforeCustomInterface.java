// "Remove 'comparing()' call" "true-preview"
import java.util.Comparator;
import java.util.function.Function;

public class MyFile {
  interface Foo extends Function<String, Integer> {}

  void test(Foo comparator) {
    Comparator<String> cmp = Comparator.comparing(String::length).thenComparing(Comparator.comp<caret>aring(comparator));
  }
}
