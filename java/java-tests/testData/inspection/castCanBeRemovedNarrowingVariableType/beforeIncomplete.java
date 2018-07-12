// "Change type of 'list' to 'ArrayList<String>' and remove cast" "true"
import java.util.*;

class Test {
  void test() {
    List<String> list = new ArrayList<>();
    ((ArrayList<caret><String>) list).ensureCapacity();
  }
}