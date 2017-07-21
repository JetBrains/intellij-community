// "Replace with collect" "true"
import java.util.*;

public class Test {
  public static void main(String[] args) {
    final double[] array = new double[10];
    final List<Double> list = new ArrayList<>();
    // Cannot be replaced with list.addAll()
    for (double d : ar<caret>ray) {
      list.add(d);
    }
  }
}
