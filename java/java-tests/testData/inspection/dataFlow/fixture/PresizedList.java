import java.util.*;

public class PresizedList {
  void test(int size) {
    List<String> list = new ArrayList<>(size);
    for (int i = 0; <warning descr="Condition 'i < list.size()' is always 'false'">i < <warning descr="Result of 'list.size()' is always '0'">list.size()</warning></warning>; i++) {
      list.add(i, "");
    }
  }
}