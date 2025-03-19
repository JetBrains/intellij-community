// "Replace with old style 'switch' statement" "true"


import java.util.function.BiFunction;

public class AAA {
  public static void main(String[] args) {
    BiFunction<Integer, Integer, Integer> operation = getOperation(1);
    System.out.println(operation.apply(5, 3));
  }

  private static BiFunction<Integer, Integer, Integer> getOperation(int operationCode) {
    return sw<caret>itch (operationCode) {
      case 1 -> (a, b) -> a + b; // Addition
      case 2 -> (a, b) -> a - b; // Subtraction
      case 3 -> (a, b) -> a * b; // Multiplication
      case 4 -> (a, b) -> a / b; // Division
      default -> (a, b) -> 0; // Default case
    };
  }

}
