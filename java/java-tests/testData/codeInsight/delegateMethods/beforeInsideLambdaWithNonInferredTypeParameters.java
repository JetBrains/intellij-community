
import java.util.function.Function;

class Test {

  public void foo() {

    foo(s -> {
      new Function<String, String>() {
        {
          <caret>
        }
      }
    })

  }
}