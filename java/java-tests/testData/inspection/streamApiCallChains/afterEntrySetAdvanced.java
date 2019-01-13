// "Fix all 'Stream API call chain can be simplified' problems in file" "true"
import java.util.Map;
import java.util.stream.Collectors;

class Scratch {
  void testSimple(Map<String, String> map) {
    String res = map.values().stream().map(String::trim).collect(Collectors.joining(";"))
    String res2 = map.keySet().stream().map(String::trim).collect(Collectors.joining(";"))
    String res3 = map.entrySet().stream().map(e -> e.toString().trim()).collect(Collectors.joining(";"))
  }

  void testFilter(Map<String, String> map) {
    String res = map.values().stream().filter(s -> !s.isEmpty()).map(String::trim)
      .collect(Collectors.joining(";"))
    String res2 = map.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(e -> e.getKey().trim())
      .collect(Collectors.joining(";"))
    String res3 = map.keySet().stream().filter(s -> !s.isEmpty()).map(String::trim)
      .collect(Collectors.joining(";"))
  }

  void testFilter2(Map<String, String> map) {
    String res = map.values().stream().filter(s -> !s.isEmpty())
      .filter(s -> s.startsWith("foo"))
      .map(String::trim)
      .collect(Collectors.joining(";"))
    String res2 = map.entrySet().stream().filter(e -> !e.getValue().isEmpty())
      .filter(e -> e.getKey().startsWith("foo"))
      .map(e -> e.getKey().trim())
      .collect(Collectors.joining(";"))
    String res3 = map.keySet().stream().filter(s -> !s.isEmpty())
      .filter(s -> s.startsWith("foo"))
      .map(String::trim)
      .collect(Collectors.joining(";"))
  }

  void testDecl(Map<String, String> map) {
      /*5*//*6*/
      String res = map.values(/*0*/).stream().filter(val1 -> {
          /*1*/
          return !val1./*2*/isEmpty();
      }).filter(val2 -> {
          return val2.startsWith("foo");
      }).map(/*3*//*4*/String::trim).collect(Collectors.joining(";"))

  }

  void testUnboxing(Map<String, Integer> map) {
    int sum = map.values().stream().filter(integer -> integer > 0)
      .mapToInt(integer -> integer + 1).sum();
    int sum2 = map.values().stream().filter(integer -> integer > 0)
      .mapToInt(integer -> integer).sum();
    int sum3 = map.values().stream().filter(integer -> integer > 0)
      .mapToInt(i -> i).sum();
  }
}