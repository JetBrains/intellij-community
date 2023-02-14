// "Remove 'map()' call" "true"
import java.util.stream.Stream;

public class Test {
  public void test() {
    System.out.println(Stream.of(/*just one number*/123).m<caret>ap((i) -> /*redundant*/ (i)).count());
  }
}
