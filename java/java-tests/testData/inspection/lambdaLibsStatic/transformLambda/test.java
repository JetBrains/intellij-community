import com.google.common.collect.Iterables;
import com.google.common.base.Function;

import java.lang.String;
import java.util.Collections;

class c {
  Iterable<String> transform = Iterables.tra<caret>nsform(Collections.emptyList(), new Function<String, String>() {
    @Override
    public String apply(String input) {
      Collectors c;
      ArrayList l;
      System.out.println(input);
      //do something
      int i = 1;
      return input.intern();
    }
  });
}