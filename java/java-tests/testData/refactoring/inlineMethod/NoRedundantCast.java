import java.util.ArrayList;
import java.util.List;

class X {
  public static List<Integer> prepare(ArrayList<Integer> list) {
    return doSome<caret>(list);
  }

  public static List<Integer> doSome(List<Integer> events) {
    Integer last = events.get(events.size() - 1);
    return events;
  }
}