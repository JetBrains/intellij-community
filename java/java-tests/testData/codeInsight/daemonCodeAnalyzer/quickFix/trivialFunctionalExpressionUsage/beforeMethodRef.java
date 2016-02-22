// "Replace method call on method reference with corresponding method call" "true"
import java.util.function.Supplier;

class Test {
  String s = ((Supplier<String>) this::foo).ge<caret>t();

  private String foo() {
    return null;
  }
}