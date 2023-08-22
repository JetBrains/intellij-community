// "Assert 'container != null'" "true-preview"
import java.util.function.Supplier;

class A{
  void test(){
    Object container = Math.random() > 0.5 ? "" : null;
    Supplier<String> r = () -> {
        if (Math.random() > 0.5) {
            assert container != null;
            return container.toString();
        } else {
            return "";
        }
    };
  }
}