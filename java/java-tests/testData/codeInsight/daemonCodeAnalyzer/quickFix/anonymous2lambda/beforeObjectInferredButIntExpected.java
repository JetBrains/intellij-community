// "Replace with lambda" "false"
import java.util.function.Function;

class Test {
  void ab() {
    comparing(new Func<caret>tion<Integer, String>() {
      public String apply(Integer pObj) {
        return Integer.toString(pObj);
      }
    });

  }

  static <T> void comparing(Function<T, String> keyExtractor){}

}
