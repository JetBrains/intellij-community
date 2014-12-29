// "Replace lambda with method reference" "false"
import java.util.function.Function;
class Example {
 
  public String m(String... s) {
    return "";
  }

  {
    Function<String, String> r = (s) -> m(s, s);
  }
}