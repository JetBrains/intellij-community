// "Replace with 'List.of' call" "true"
import java.util.*;

public class Test {
  public void test() {
    List<String[]> list = List.<String[]>of(new String[]{"aaa"});
  }
}