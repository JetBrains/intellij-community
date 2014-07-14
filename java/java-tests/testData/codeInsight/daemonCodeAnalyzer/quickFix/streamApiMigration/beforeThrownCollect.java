// "Replace with collect" "false"
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ForEachTest {

  interface A {
    String ii() throws IOException;
  }
  private List<A> reqs;

  public ForEachTest () throws IOException {
    List<String> result = new ArrayList<>();
    for(A val : re<caret>qs) {
      result.add(val.ii());
    }
  }
}
