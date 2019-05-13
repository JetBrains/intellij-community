// "Replace with lambda" "false"
import java.util.function.Function;

class Test {
  private static final Function<String, String> FUN = new Fun<caret>ction<String, String>() {
    @Override
    public String apply(final String s) {
      return B_FUN.apply(s);
    }
  };


  private static final Function<String, String> B_FUN = null;
}
