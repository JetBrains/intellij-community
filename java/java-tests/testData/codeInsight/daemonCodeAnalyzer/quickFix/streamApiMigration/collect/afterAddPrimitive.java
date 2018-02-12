// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Test {
  public static void main(String[] args) {
    final double[] array = new double[10];
      final List<Double> list = Arrays.stream(array).boxed().collect(Collectors.toList());
    // Cannot be replaced with list.addAll()
  }
}
