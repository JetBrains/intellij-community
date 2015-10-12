import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main10 {

  void m() {
    FluentIterable<String> it = Fluent<caret>Iterable.from(new ArrayList<>());

    ArrayList<String> al = new ArrayList<>();
    ArrayList<String> al2 = new ArrayList<>();
    it.transform(s -> s + "asd").copyInto(al.size() > 10 ? al : al2);
  }
}