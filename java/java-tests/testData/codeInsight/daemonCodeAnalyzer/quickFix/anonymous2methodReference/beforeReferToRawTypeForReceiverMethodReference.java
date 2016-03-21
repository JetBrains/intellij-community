// "Replace with method reference" "true"
import java.util.List;
import java.util.function.Function;

class MyTest {

  Function<List<?>, String> TO_TEXT = new Func<caret>tion<List<?>, String>() {
    @Override
    public String apply(List<?> lst) {
      return lst.toString();
    }
  };

}
