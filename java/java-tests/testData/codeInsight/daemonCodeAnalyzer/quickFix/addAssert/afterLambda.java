// "Assert 'container != null'" "true"
import java.util.function.Supplier;

class A{
  void test(){
    Object container = null;
    Supplier<String> r = () -> {
        if (container == null) {
            assert container != null;
            return container.toString();
        } else {
            return "";
        }
    };
  }
}