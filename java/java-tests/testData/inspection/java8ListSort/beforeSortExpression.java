// "Replace with List.sort" "true"
import java.util.Collections;
import java.util.List;

public class Main {
  public static void doSort(List<String> list1, List<String> list2, boolean b) {
    Collections.so<caret>rt(b ? list1 : list2, String.CASE_INSENSITIVE_ORDER);
  }
}