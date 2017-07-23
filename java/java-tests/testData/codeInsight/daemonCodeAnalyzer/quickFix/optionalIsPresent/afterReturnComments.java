// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  static class MyList<T extends Number> extends ArrayList<T> {
    @Override
    public T get(int index) {
      return super.get(index);
    }
  }

  public Number testOptionalComments(Optional<MyList> strList) {
      /* optional is present */
      /*return something */
      /* optional is absent */
      /* return null*/
      return strList.map(myList -> myList.size() > /*too big*/ 1 ? myList.get(1) : 1.0).orElse(null);
  }
}