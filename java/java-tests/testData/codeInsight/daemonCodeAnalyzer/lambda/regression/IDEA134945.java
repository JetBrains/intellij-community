import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MyTest {

  public static void main(String[] args) {
    List<Test> data = new ArrayList<>();
    Map<String, Map<String, Integer>> result = data.stream().collect(
      Collectors.groupingBy(
        Test::getName,
        Collectors.groupingBy(
          Test::getMonth,
          Collectors.summingInt(Test::getAmount))
      )
    );
  }


  private static class Test{
    private String name;
    private String month;
    private int amount;

    public Test(String name, String month, int amount) {
      this.name = name;
      this.month = month;
      this.amount = amount;
    }

    public String getName() {
      return name;
    }

    public String getMonth() {
      return month;
    }

    public int getAmount() {
      return amount;
    }
  }

}