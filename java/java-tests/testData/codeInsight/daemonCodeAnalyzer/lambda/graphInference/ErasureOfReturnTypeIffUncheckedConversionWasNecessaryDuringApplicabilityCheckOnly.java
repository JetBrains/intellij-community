import java.util.List;
import java.util.stream.Collectors;

class Test {
  private static List<Object> test(List<List> list) {
    return list.stream().flatMap(List::stream).<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.util.List<java.lang.Object>'">collect</error>(Collectors.toList());
  }

  private static List<Object> test1(List<List> list) {
    return list.stream().flatMap(l -> l.stream()).<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.util.List<java.lang.Object>'">collect</error>(Collectors.toList());
  }
}
