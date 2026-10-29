import java.util.*;

class DiamondJava6 {
  List<String> field = new ArrayList<error descr="Diamond types are not supported at language level '6'"><></error>();

  void test() {
    List<String> explicit = new ArrayList<String>();
    Map<String, List<Integer>> map = new HashMap<error descr="Diamond types are not supported at language level '6'"><></error>();
    Comparator<String> anonymous = new Comparator<error descr="Diamond types are not supported at language level '6'"><></error>() {
      public int compare(String a, String b) { return 0; }
    };
  }
}
