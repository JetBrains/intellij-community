// "Replace with 'List.of' call" "false"
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Test {
  public static final List<Number> EVEN = Collections.unmodi<caret>fiableList(Arrays.asList(2,4,6,8,10,Math.random() > 0.5 ? 12 : null));
}
