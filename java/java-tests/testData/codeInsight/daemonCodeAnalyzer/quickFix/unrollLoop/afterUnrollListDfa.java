// "Unroll loop" "true"
import java.util.List;

class X {
  void testList(List<String> list) {
    if (list.size() == 2) {
        System.out.println(list.get(0));
        System.out.println(list.get(1));
    }
  }
}