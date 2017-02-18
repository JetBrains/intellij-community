// "Replace method call on lambda with lambda body" "true"
import java.util.function.Supplier;

class Test {
  String s = ((Supplier<String>) () -> {
    // comment
    return "";
  }).g<caret>et();
}