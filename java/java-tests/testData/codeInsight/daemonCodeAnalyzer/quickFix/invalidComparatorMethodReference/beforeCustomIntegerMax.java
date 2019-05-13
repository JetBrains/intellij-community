// "Replace with Comparator.naturalOrder()" "false"
import java.util.Arrays;
import java.util.List;

public class Main {
  static class Integer {
    static int max(int a, int b) {
      return a == b ? 0 : a < b ? -1 : 1;
    }
  }

  public static void main(String[] args) {
    List<java.lang.Integer> ints = Arrays.asList(3,12,-2,3,-1,-4,4,args.length);
    System.out.println(ints.stream().max(Int<caret>eger::max));
  }
}
