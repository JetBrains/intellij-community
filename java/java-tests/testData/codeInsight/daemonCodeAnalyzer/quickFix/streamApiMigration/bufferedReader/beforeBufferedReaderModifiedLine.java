// "Collapse loop with stream 'sum()'" "true-preview"

import java.io.BufferedReader;
import java.io.IOException;

public class Main {
  void test(BufferedReader br) throws IOException {
    String line = "";
    long count = 0;
    wh<caret>ile((line = br.readLine()) != null) {
      line = line.trim();
      count+=line.length();
    }
    System.out.println(count);
  }
}