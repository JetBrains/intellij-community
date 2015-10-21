import com.google.common.collect.FluentIterable;
import java.util.ArrayList;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> it = FluentIte<caret>rable.from(strings).transform(String::trim);
    System.out.println(it.size());
  }
}