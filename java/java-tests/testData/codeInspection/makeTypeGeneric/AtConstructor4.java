import java.util.*;

public class AtConstructor4 {
  void test(List<Integer> input) {
    List<String> list = new ArrayList<caret>(input);
  }
}