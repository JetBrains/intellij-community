import java.util.*;

public class Usage {
  private void demo() {
    callme(Collections.<<caret>>emptyList());
  }

  private void callme(List<String> items) {
  }

}
