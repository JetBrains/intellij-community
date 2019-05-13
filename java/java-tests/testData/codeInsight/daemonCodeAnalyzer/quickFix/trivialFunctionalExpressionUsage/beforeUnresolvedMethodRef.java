// "Replace method call on method reference with corresponding method call" "false"
import java.util.function.Supplier;

class Test {
  String s = ((Supplier<String>) this::foo).ge<caret>t();
}