// "Remove redundant cast(s)" "true-preview"
import java.util.stream.*;

class Test {
  {
    Stream.of(1,2,3)
      .map(Integer.class::<caret>cast)
      .forEach(System.out::println)
  }
}