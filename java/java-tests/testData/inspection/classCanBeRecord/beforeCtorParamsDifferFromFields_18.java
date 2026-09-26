// "Convert to record class" "false"

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// This test makes sure we don't change semantics (overload resolution).
class Main {
  public static void main(String[] args) {
    // T is instantiated as String
    List<String> stringList = new ArrayList<>();
    new Selector<>(stringList);

    // T is instantiated as Integer
    List<Integer> integerList = new ArrayList<>();
    new Selector<>(integerList);
  }
}

class <caret>Selector<T> {
  private final List<String> list;

  Selector(Collection<T> collection) {
    this.list = collection.stream().map(Object::toString).toList();
  }
}