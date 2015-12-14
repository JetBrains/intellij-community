import com.google.common.collect.FluentIterable;
import java.util.ArrayList;

public class Test {
  public FluentIterable<String> create() {
    return FluentIterable.from(new ArrayList<String>()).transform(s -> s + s);
  }

  public FluentIterable<String> create2() {
    return create().limit(12);
  }

  public FluentI<caret>terable<String> create3() {
    return create2().limit(12);
  }

}