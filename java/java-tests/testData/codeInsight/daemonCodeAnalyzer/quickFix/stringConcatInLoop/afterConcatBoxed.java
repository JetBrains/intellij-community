// "Fix all 'String concatenation in loop' problems in file" "true"

public class Main {
  void test(String[] strings) {
    StringBuilder result1 = new StringBuilder();
    StringBuilder result2 = new StringBuilder();
    StringBuilder result3 = new StringBuilder();
    StringBuilder result4 = new StringBuilder();
    StringBuilder result5 = new StringBuilder();
    StringBuilder result6 = new StringBuilder();
    for (Integer i = 0; i < strings.length; i++) {
      result1.append(i + 1).append(" item: ").append(strings[i]).append(" ");
      result2.append(1 + i).append(" item: ").append(strings[i]).append(" ");
      result3.append(i).append(" item: ").append(1).append(strings[i]).append(" ");
      result4.append(" item: ").append(i).append(1).append(strings[i]).append(" ");
      result5.append(" item: " + 1).append(i).append(strings[i]).append(" ");
      result6.append(1 + " item: ").append(i).append(strings[i]).append(" ");
    }
  }
}
