// "Insert '.test' to call functional interface method" "true"
import java.util.function.Function;
import java.util.function.Predicate;

public class Test {
  public void test(Predicate<String> predicate) {
    if(predicate.test("foo")) {

    }
  }
}