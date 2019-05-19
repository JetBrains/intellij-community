// "Fix all 'Stream API call chain can be simplified' problems in file" "true"
import java.util.Map;
import java.util.stream.Collectors;

class Scratch {
  void testSimple(Map<String, String> map) {
    String res = map.entrySet().stream().m<caret>ap(e -> e.getValue().trim()).collect(Collectors.joining(";"))
    String res2 = map.entrySet().stream().map(e -> e.getKey().trim()).collect(Collectors.joining(";"))
    String res3 = map.entrySet().stream().map(e -> e.toString().trim()).collect(Collectors.joining(";"))
  }

  void testFilter(Map<String, String> map) {
    String res = map.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(e -> e.getValue().trim())
      .collect(Collectors.joining(";"))
    String res2 = map.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(e -> e.getKey().trim())
      .collect(Collectors.joining(";"))
    String res3 = map.entrySet().stream().filter(e -> !e.getKey().isEmpty()).map(e -> e.getKey().trim())
      .collect(Collectors.joining(";"))
  }

  void testFilter2(Map<String, String> map) {
    String res = map.entrySet().stream().filter(e -> !e.getValue().isEmpty())
      .filter(e -> e.getValue().startsWith("foo"))
      .map(e -> e.getValue().trim())
      .collect(Collectors.joining(";"))
    String res2 = map.entrySet().stream().filter(e -> !e.getValue().isEmpty())
      .filter(e -> e.getKey().startsWith("foo"))
      .map(e -> e.getKey().trim())
      .collect(Collectors.joining(";"))
    String res3 = map.entrySet().stream().filter(e -> !e.getKey().isEmpty())
      .filter(e -> e.getKey().startsWith("foo"))
      .map(e -> e.getKey().trim())
      .collect(Collectors.joining(";"))
  }

  void testDecl(Map<String, String> map) {
    String res = map.entrySet(/*0*/).stream().filter(e -> {
      String val1 = e./*1*/getValue();
      return !val1./*2*/isEmpty();
    }).filter(e -> {
      String val2 = e.getValue();
      return val2.startsWith("foo");
    }).map((Entry<String, String> /*3*/e/*4*/) -> {
      String val3 = e.getValue()/*5*/;
      return val3.trim(/*6*/);
    }).collect(Collectors.joining(";"))

  }

  void testUnboxing(Map<String, Integer> map) {
    int sum = map.entrySet().stream().filter(x -> x.getValue() > 0)
      .mapToInt(x -> x.getValue() + 1).sum();
    int sum2 = map.entrySet().stream().filter(x -> x.getValue() > 0)
      .mapToInt(x -> x.getValue()).sum();
    int sum3 = map.entrySet().stream().filter(x -> x.getValue() > 0)
      .mapToInt(Map.Entry::getValue).sum();
  }
}