import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

class A {

  Function<String, String> myFunction = new Function<String, String>() {
    @Override
    public String apply(String input) {
      return input.trim();
    }
  };

  List<String> main(String[] args) {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> i<caret>t = FluentIterable.from(strings);
    it = it.transform(input -> input.intern());
    it = it.transform(String::trim);
    it = it.transform(new Function<String, String>() {
      @Override
      public String apply(String input) {
        System.out.println("do some action on " + input);
        return input.substring(0, 10);
      }
    });
    it = it.transform(myFunction);
    return it.toList();
  }

}