// "Replace Optional.isPresent() condition with map().orElse()" "true"

import java.util.*;

public class Main {
  static class MyList<T extends Number> extends ArrayList<T> {
    @Override
    public T get(int index) {
      return super.get(index);
    }
  }

  public Number testOptionalComments(Optional<MyList> strList) {
    /* optional is present *//* optional is absent */
      return strList.map(/*return something */myList -> myList.size() > /*too big*/ 1 ? myList.get(1) : 1.0).orElse(/* return null*/null);
  }
}