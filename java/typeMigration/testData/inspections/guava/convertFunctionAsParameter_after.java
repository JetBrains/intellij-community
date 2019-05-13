import com.google.common.base.Functions;

import java.util.function.Function;

public class Main16 {
  void m() {
    Function<String, String> f = s -> s.substring(12) + "12";

    Functions.compose(f::apply, f::apply);
  }
}
