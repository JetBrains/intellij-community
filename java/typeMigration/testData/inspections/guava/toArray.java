import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main10 {

  void m() {
    FluentIterable<String> i<caret>t = FluentIterable.from(new ArrayList<>());

    String[] arr = it.transform(s -> s + "asd").toArray(String.class);
  }
}
