// "Unwrap" "false"
import java.util.*;

public class Tests {
  void test3(Optional<String> optional) {
    System.out.println(Optional.of(optional.<caret>get()));
    System.out.println(Optional.of(optional.get()));
  }
}
