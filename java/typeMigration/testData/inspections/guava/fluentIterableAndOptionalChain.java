import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main {

  String m() {
    FluentIterable<String> i<caret>t = FluentIterable.from(new ArrayList<>());

    return it.transform(s -> s + "asd").firstMatch(s -> s.length() > 10).or(Optional.fromNullable("10")).orNull();
  }
}
