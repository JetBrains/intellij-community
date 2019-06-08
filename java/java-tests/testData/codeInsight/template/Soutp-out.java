import java.lang.String;
import java.util.Arrays;
import java.util.List;

public class LiveTemplateTest {

  void usage(boolean b, int[] ints, int[][] deepInts, Object[][] deepObjects) {
      System.out.println("b = " + b + ", ints = " + Arrays.toString(ints) + ", deepInts = " + Arrays.deepToString(deepInts) + ", deepObjects = " + Arrays.deepToString(deepObjects));<caret>
  }

}