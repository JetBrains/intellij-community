// "Replace method call on lambda with lambda body" "true-preview"
import java.util.function.Supplier;

class Test {
  String s = ((Supplier<String>) () -> {
    // comment
    return "";
  }).g<caret>et();
}