import com.google.common.base.Function;
import com.google.common.base.Functions;

public class Main16 {
  void m() {
    Function<String, String> <caret>f = new Function<String, String>() {
      @Override
      public String apply(String s) {
        return s.substring(12) + "12";
      }
    };

    Functions.compose(f, f);
  }
}
