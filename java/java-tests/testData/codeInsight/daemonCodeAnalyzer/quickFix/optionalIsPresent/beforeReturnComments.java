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
    if(strList.isPres<caret>ent()) {
            /* optional is present */
      return /*return something */ strList.get().size() > /*too big*/ 1 ?  strList.get().get(1) : 1.0;
    } else {
            /* optional is absent */
      return /* return null*/ null;
    }
  }
}