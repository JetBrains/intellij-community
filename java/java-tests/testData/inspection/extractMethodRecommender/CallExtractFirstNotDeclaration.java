import java.util.ArrayList;
import java.util.List;

class Test {

  public static void main(String[] args) {
  }
  List<List<String>> simpleWithPrecedingComment() {
    // Create list
                        // Comment
                        <weak_warning descr="It's possible to extract method returning 'list' from a long surrounding method">List<caret><String> list = new ArrayList<>();</weak_warning>
    list.add("one");
    list.add("two");
    list.add("three");
    list.add("four");

    List<String> list2 = new ArrayList<>();
    list2.add("v1");
    list2.add("v2");
    list2.add("v3");
    list2.add("v4");
    return List.of(list, list2);
  }
}
