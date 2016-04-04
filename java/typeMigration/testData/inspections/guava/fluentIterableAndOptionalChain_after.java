import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.stream.Stream;

public class Main {

  String m() {
    Stream<String> it = new ArrayList<String>().stream();

    return java.util.Optional.ofNullable(it.map(s -> s + "asd").filter(s -> s.length() > 10).findFirst().orElseGet(Optional.fromNullable("10")::get)).orElse(null);
  }
}
