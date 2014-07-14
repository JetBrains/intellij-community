import java.util.Arrays;
import java.util.List;

class ListComprehensionSample {
  public static void main(String[] args) {
    new ListComprehensionSample().run();
  }

  interface Function1<ResultType, ParameterType1> {
    ResultType invoke(ParameterType1 parameter1);
  }

  private void run() {
    Function1<Integer, String> stringToInt = Integer::parseInt;
    Function1<Double, Integer> intToPercent = i -> i / 100.0;
    List<String> values = Arrays.asList("12", "23", "34", "45", "56", "67", "78", "89");
    values.stream().map(stringToInt::invoke).map(intToPercent::invoke).forEach(System.out::println);
  }
}