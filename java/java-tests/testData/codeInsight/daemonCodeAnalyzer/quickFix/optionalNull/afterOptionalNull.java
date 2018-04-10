// "Fix all 'Null value for Optional type' problems in file" "true"
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class Test {
  Optional<String> field = Optional.empty();
  Optional<? extends CharSequence> field2 = Optional.empty();

  public <T> void test(List<T> list) {
    Optional<T> s = list.size() > 0 ? Optional.of(list.get(0)) : Optional.empty();
    varArg(1, Optional.of(1), Optional.empty());
    s = Optional.empty();
    m((Optional<String>) Optional.<String>empty());
    Optional.of("xyz").flatMap(x -> Optional.empty()).ifPresent(System.out::println);
  }

  OptionalInt opt() {
    return (/*comment*/OptionalInt.empty());
  }

  void m(Optional<String> opt) {}
  void m(String s) {}

  void varArg(int x, Optional<?>... opts) {

  };
}