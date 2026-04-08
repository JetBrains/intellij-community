import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class T {
  public void test() {
    List<String> list1 = createList1();
    List<Integer> list2 = new ArrayList<>();
    List<String> list3 = createList1();
  }

    private static @NotNull ArrayList<String> createList1() {
        return new ArrayList<>();
    }
}