import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main14 {

  Iterable<String> m() {
    return FluentI<caret>terable.from(new ArrayList<String>()).transform(s -> s + s).filter(String::isEmpty);
  }
}
