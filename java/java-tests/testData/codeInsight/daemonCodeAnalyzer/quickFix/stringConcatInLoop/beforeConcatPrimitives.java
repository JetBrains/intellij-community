// "Fix all 'String concatenation in loop' problems in file" "true"

public class Main {
  void test(String[] strings) {
    String result1 = "";
    String result2 = "";
    String result3 = "";
    String result4 = "";
    String result5 = "";
    String result6 = "";
    for (int i = 0; i < strings.length; i++) {
      result1 +=<caret> i + 1 + " item: " + strings[i] + " ";
      result2 += 1 + i + " item: " + strings[i] + " ";
      result3 += i + " item: " + 1 + strings[i] + " ";
      result4 += " item: " + i + 1 + strings[i] + " ";
      result5 += " item: " + 1 + i + strings[i] + " ";
      result6 += 1 + " item: " + i + strings[i] + " ";
    }
  }
}
