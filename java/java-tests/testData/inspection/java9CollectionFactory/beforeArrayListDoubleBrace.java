// "Replace with 'List.of' call" "true"
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Test {
  public static final List<Number> EVEN = Collections.unmodif<caret>iableList(new ArrayList<>() {{
    this.add(0);
    this.add(2);
    this.add(4);
    this.add(6);
    this.add(8);
  }});
}
