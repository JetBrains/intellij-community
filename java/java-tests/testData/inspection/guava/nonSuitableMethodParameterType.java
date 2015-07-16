import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main20 {

  void get() {
    FluentIterable<String> i = FluentIterable.fr<caret>om(new ArrayList<String>());
    m(i);
  }

  void m(FluentIterable abc) {

  }

}