import java.util.List;
import java.util.stream.*;

public class MutabilityJdk21 {

  private final List<Integer> list = Stream.of(1, 2, 3).toList();

  void testFieldList(){
    list.<warning descr="Immutable object is modified">add</warning>(4);
  }

  void testToList() {
    List<Integer> l = Stream.of(1, 2, 3)
      .toList();
    l.<warning descr="Immutable object is modified">add</warning>(4);
  }
}
