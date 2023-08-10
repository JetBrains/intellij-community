// "Replace Optional presence condition with functional style expression" "false"
import java.util.Optional;

class Test {
  Object get() {
    Optional<String> obj = Stream.of("one", "two").filter(s -> Math.sqrt(s.length()) > 100).findFirst();
    if (obj.<caret>isPresent()) {
      return obj.get();
    } else {
      return 7;
    }
  }

}