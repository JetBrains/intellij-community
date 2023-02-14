// "Remove 'map()' call" "true"
import java.util.stream.Stream;

public class Test {
  public void test() {
      /*redundant*/
      System.out.println(Stream.of(/*just one number*/123).count());
  }
}
