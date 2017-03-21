// "Replace with 'List.of' call" "true"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Test {
  public static final List<Number> EVEN = Collections.unmo<caret>difiableList(new ArrayList<>(Arrays.asList(2,4,6,8,10)));
}
