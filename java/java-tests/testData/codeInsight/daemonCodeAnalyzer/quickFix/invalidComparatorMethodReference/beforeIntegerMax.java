// "Replace with 'Comparator.naturalOrder()'" "true-preview"
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  public static void main(String[] args) {
    List<Integer> ints = Arrays.asList(3,12,-2,3,-1,-4,4,args.length);
    System.out.println(ints.stream().max(Int<caret>eger::max));
  }
}
