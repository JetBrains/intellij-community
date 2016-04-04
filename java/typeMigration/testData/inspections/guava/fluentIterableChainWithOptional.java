import java.util.*;
import com.google.common.collect.FluentIterable;

class A {
  void m1() {
    ArrayList<String> strings = new ArrayList<String>();
    String str = FluentIterable.from(strings).transform(s -> s + s).lim<caret>it(10).firstMatch(s -> s.isEmpty()).orNull();
    System.out.println("s: " + str);
  }
}