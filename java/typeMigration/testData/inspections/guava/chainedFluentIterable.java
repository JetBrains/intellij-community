import com.google.common.base.Predicate;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> i<caret>t = FluentIterable.from(strings);

    List<Boolean> booleans = it.transform(String::isEmpty).toList();

    boolean empty = it.transform(s -> s.trim()).transform(new Function<String, char[]>() {
      @Override
      public char[] apply(String input) {
        return input.toCharArray();
      }
    }).skip(777).filter(new Predicate<char[]>() {
      @Override
      public boolean apply(char[] input) {
        return input.length != 10;
      }
    }).isEmpty();
  }
}