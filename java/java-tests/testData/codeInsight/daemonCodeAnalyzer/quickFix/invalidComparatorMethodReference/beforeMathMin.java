// "Replace with Comparator.reverseOrder()" "true"
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  public static void main(String[] args) {
    List<Integer> ints = Arrays.asList(3,12,-2,3,-1,-4,4,args.length);
    ints.sort(Math::m<caret>in);
    System.out.println(ints);
  }
}
