// "Replace with sum()" "false"

import java.io.BufferedReader;
import java.io.IOException;

public class Main {
  void test(BufferedReader br) throws IOException {
    String line = "";
    long count = 0;
    wh<caret>ile((line = br.readLine()) != null) {
      String trimmed = line.trim();
      count+=trimmed.length();
    }
    System.out.println(count+":"+line);
  }
}