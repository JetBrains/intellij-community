import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class MainTest {
  public static void main(String[] args) {
    List<String> list = <flown111>new ArrayList<>();
    List<String> unmodifiable = <flown1>Collections.unmodifiableList(<flown11>list);
    System.out.println(<caret>unmodifiable);
  }
}