// "Remove type arguments" "false"
import java.util.Map.Entry;
import java.util.stream.Collectors;

class Foo {
  {
    Collectors.toMap(Entry<Integer, <caret>Integer>::getKey, null);
  }
}