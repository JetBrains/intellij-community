import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main20 {

  void get() {
    FluentIterable<String> i = FluentIterable.f<caret>rom(new ArrayList<String>()).cycle();
  }

}