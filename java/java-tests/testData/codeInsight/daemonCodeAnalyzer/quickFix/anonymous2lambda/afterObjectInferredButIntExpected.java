// "Replace with lambda" "true-preview"
import java.util.function.Function;

class Test {
  void ab() {
    comparing((Function<Integer, String>) pObj -> Integer.toString(pObj));

  }

  static <T> void comparing(Function<T, String> keyExtractor){}

}
