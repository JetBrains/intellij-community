// "Replace method call on method reference with corresponding method call" "true-preview"
import java.util.function.Supplier;

class Test {
  String s = ((Supplier<String>) this::foo).ge<caret>t();

  private String foo() {
    return null;
  }
}