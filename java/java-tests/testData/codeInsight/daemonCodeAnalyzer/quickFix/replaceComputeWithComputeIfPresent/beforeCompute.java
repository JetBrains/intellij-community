// "Replace 'compute' with 'computeIfPresent'" "true"
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Main {
  interface Item {
    Integer getInfo(int i);
  }

  native List<String> getNamesList();

  void test(List<Item> infoList) {
    Map<String, List<Integer>> data = new HashMap<>();
    List<String> names = getNamesList();
    for (String key : names) {
      data.put(key, new ArrayList<>());
    }
    for (Item info : infoList) {
      for (int i = 0; i < names.size(); i++) {
        final String k = names.get(i);
        final Integer newValue = info.getInfo(i);
        data.compute(k, (key, array) -> {
          array.a<caret>dd(newValue);
          return array;
        });
      }
    }
  }
}