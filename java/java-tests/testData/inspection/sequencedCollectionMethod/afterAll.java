// "Fix all 'SequencedCollection method can be used' problems in file" "true"
import java.util.*;

interface Foo extends SequencedCollection<String> {}

public class Test {
  public static void main(Foo foo, String[] args) {
    List<String> list = List.of(args);

    var e1 = foo.getFirst();
    var e2 = list.getFirst();
    var e3 = list.getLast();
    var e4 = list.removeFirst();
    var e5 = list.removeLast();
    list.remove("e");
    list.get(1);
  }
  
  void testAdd(List<String> list) {
    list.addFirst("hello");
  }
  
  void testSeries(List<String> list) {
    System.out.println(list.get(0));
    System.out.println(list.get(1));
    System.out.println(list.get(2));
  }
}
