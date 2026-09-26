import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class MyClass {
  public static void main(String[] args) {
    // Just a class to break static code analysis.
    MyClass myClass = new MyClass();
    Map<String, Integer> things = myClass.getSomethings();
    // We try to group the keys based on some transformation function
    Map<String, Set<String>> myNewThings = things.entrySet().stream().collect(
      Collectors.toMap(entry -> myClass.groupKey(entry.getKey()),
                       entry -> Collections.singleton(entry.getKey()),
                       (key1, key2) -> {
                         Set<String> newValues;
                         if (key1.size() > 1) {
                           newValues = key1;
                           newValues.addAll(key2);
                         } else if (key2.size() > 1) {
                           newValues = key2;
                           newValues.addAll(key1);
                         } else {
                           newValues = new HashSet<>();
                           newValues.addAll(key1);
                           newValues.addAll(key2);
                         }
                         return newValues;
                       }
      )
    );
  }

  public String groupKey(final String key) {
    return "onsomekey";
  }

  public Map<String, Integer> getSomethings() {
    Map<String, Integer> theMap = new HashMap<>();
    theMap.put("someKey", 1);
    theMap.put("someOtherKey", 2);
    theMap.put("andAnotherKey", 3);
    return theMap;
  }
}