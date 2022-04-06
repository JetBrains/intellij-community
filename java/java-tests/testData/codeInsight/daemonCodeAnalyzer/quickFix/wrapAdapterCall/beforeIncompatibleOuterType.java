// "Adapt using 'Collections.singletonList()'" "true"
import java.util.*;

class Test {
  List<String> list(String element) {
    return Objects<caret>.requireNonNull(element);
  }
}