import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class Test {

  public static void main(String[] args) {
  }
  List<List<String>> simpleWithPrecedingComment() {
    // Create list
                        // Comment
      List<String> list = getStrings();

      List<String> list2 = new ArrayList<>();
    list2.add("v1");
    list2.add("v2");
    list2.add("v3");
    list2.add("v4");
    return List.of(list, list2);
  }

    private static @NotNull List<String> getStrings() {
        List<String> list = new ArrayList<>();
        list.add("one");
        list.add("two");
        list.add("three");
        list.add("four");
        return list;
    }
}
