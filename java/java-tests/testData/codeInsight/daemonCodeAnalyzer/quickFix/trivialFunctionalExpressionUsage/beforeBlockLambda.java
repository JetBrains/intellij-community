// "Replace method call on lambda with lambda body" "true"
import java.util.function.Supplier;

class Test {
  {
    String str = ((Supplier<String>)() -> {
      String s = "";
      return s;
    }).g<caret>et();
  }
}