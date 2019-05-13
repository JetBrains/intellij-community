import com.google.common.collect.Iterables;
import com.google.common.base.Function;

import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

class c {
  void m() {
    final Function<String, String> function = (Function<String, String>)new Function<String, String>() {
      @Override
      public String apply(String input) {
        Collectors c;
        ArrayList l;
        System.out.println(input);
        //do something
        int i = 1;
        return input;
      }
    };
    Collections.<String>emptyList().stream().map(function::apply).collect(Collectors.toList());
  }
}