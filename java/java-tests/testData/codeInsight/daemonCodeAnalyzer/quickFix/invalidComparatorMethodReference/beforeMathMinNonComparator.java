// "Replace with Comparator.reverseOrder()" "false"
import java.util.function.IntBinaryOperator;

public class Main {
  public static void main(String[] args) {
    IntBinaryOperator op = Math::m<caret>in;
    System.out.println(op.applyAsInt(1, 2));
  }
}
