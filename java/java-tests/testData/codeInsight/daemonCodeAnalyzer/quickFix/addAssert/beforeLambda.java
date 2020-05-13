// "Assert 'container != null'" "true"
import java.util.function.Supplier;

class A{
  void test(){
    Object container = null;
    Supplier<String> r = () -> container == null ? container.toS<caret>tring() : "";
  }
}