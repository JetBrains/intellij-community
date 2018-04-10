// "Fix all 'Null value for Optional type' problems in file" "true"
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class Test {
  Optional<String> field = null;
  Optional<? extends CharSequence> field2 = null;

  public <T> void test(List<T> list) {
    Optional<T> s = list.size() > 0 ? Optional.of(list.get(0)) : n<caret>ull;
    varArg(1, Optional.of(1), null);
    s = null;
    m((Optional<String>) null);
    Optional.of("xyz").flatMap(x -> null).ifPresent(System.out::println);
  }

  OptionalInt opt() {
    return (/*comment*/null);
  }

  void m(Optional<String> opt) {}
  void m(String s) {}

  void varArg(int x, Optional<?>... opts) {

  };
}