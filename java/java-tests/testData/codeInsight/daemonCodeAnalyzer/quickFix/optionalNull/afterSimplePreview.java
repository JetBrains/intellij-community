// "Replace with 'Optional.empty()'" "true-preview"
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public class Test {
  Optional<Integer> foo(boolean flag) {
    return flag ? Optional.of(42) : Optional.empty();
  }
}