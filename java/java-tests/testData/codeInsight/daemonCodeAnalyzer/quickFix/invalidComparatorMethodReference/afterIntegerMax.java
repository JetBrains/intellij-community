// "Replace with Comparator.naturalOrder()" "true"
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {
  public static void main(String[] args) {
    List<Integer> ints = Arrays.asList(3,12,-2,3,-1,-4,4,args.length);
    System.out.println(ints.stream().max(Comparator.naturalOrder()));
  }
}
