// "Replace method call on method reference with corresponding method call" "true"
import java.util.function.Supplier;

class Test {
  String s = foo();

  private String foo() {
    return null;
  }
}