// "Change type of 'list' to 'ArrayList<String>' and remove cast" "true"
import java.util.*;

class Test {
  void test() {
    ArrayList<String> list = new ArrayList<>();
    list.ensureCapacity();
  }
}