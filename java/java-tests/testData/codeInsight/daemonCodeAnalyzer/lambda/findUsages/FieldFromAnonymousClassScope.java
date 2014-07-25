import java.util.Collection;
import java.util.Map;

class Test{

  public static void main(String[] args) {
    Map<String, Collection<Integer>> myMap = null;

    myMap.entrySet().stream()
      .flatMap(entry -> entry.getValue().stream()
        .map(val -> new Object() {
          String key = entry.getKey();
          Integer va<caret>lue = val;
        }))
      .forEachOrdered(o -> {
        final String key = o.key;
        final Integer value = o.value;
        System.out.println("key: " + key + " value: " + value);
      });
  }
}