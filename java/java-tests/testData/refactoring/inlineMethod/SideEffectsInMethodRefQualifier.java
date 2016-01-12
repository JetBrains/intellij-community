
import java.util.function.Supplier;

public class Test {

  {
    Supplier<String> sup = new Test()::get;
  }

  private String ge<caret>t() {
    return null;
  }
}