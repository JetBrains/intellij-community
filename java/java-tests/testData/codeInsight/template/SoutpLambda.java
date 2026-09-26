import java.lang.String;
import java.util.List;
import java.util.function.Consumer;

public class LiveTemplateTest {

  void usage(boolean b, int[] ints, int[][] deepInts, Object[][] deepObjects) {
    Consumer<String> cons = s -> {
      <caret>
    }
  }

}