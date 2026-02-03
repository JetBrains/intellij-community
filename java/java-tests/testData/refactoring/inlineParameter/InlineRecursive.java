import java.util.*;

public class ExpData {
   private final List<Integer> myResult = new ArrayList<Integer>();
  {
    m(10, myResult);
  }

  private void m(int i, List<Integer> re<caret>sult) {
    if (i > 0) {
      m(i - 1, result);
    }
    result.add(i);
  }
}
