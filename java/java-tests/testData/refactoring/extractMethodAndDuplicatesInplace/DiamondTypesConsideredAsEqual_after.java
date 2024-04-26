import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class T {
  public void test() {
    List<String> list1 = getList1();
    List<Integer> list2 = new ArrayList<>();
    List<String> list3 = getList1();
  }

    private static @NotNull ArrayList<String> getList1() {
        return new ArrayList<>();
    }
}