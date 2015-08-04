import com.google.common.collect.Iterables;
import com.google.common.base.Function;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Iterables.transf<caret>orm(Collections.emptyList(), new Function<String, String> () {
      @Override
      public String apply(String input) {
        java.util.stream.Collectors c;
        java.util.ArrayList l;
        System.out.println(input);
        //do something
        int i = 1;
        return input;
      }
    })
  }
}