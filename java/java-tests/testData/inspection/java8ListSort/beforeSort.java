// "Replace with List.sort" "true"
import java.util.Collections;
import java.util.List;

public class Main {
  public static void doSort(List<String> list) {
    Collections.so<caret>rt(list, String.CASE_INSENSITIVE_ORDER);
  }
}