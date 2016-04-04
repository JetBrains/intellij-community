// "Replace method call on lambda with lambda body" "true"
import java.util.function.Supplier;

class Test {
  String s = ((Supplier<String>) () -> {
    return "";
  }).g<caret>et();
}