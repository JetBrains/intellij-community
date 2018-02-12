// "Replace with sum()" "true"

import java.util.List;

public class Main {
  public void testPrimitiveMap(List<String> data) {
    int sum = 0;
    if(Math.random() > 0.5) {
      System.out.println("oops");
    } else {
        sum = data.stream().filter(str -> str.startsWith("xyz")).mapToInt(String::length).filter(len -> len > 10).map(len -> len * 2).sum();
    }
    System.out.println(sum);
  }
}