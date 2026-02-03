import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main8 {

  void m() {
    String value = getOptional().orNull();
  }

  Option<caret>al<String> getOptional() {
    return FluentIterable.from(new ArrayList<String>()).transform(x -> x + x).firstMatch(String::isEmpty);
  }
}