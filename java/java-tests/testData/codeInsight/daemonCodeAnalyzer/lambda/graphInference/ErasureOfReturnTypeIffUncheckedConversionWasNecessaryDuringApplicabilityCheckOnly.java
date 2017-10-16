import java.util.List;
import java.util.stream.Collectors;

class Test {
  private static List<Object> test(List<List> list) {
    return list.stream().flatMap(List::stream).collect(Collectors.toList());
  }

  private static List<Object> test1(List<List> list) {
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.util.List<java.lang.Object>'">return list.stream().flatMap(l -> l.stream()).collect(Collectors.toList());</error>
  }
}
