// "Replace with collect" "false"

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
  List<String> test(List<BufferedReader> readers) throws IOException {
    List<String> result = new ArrayList<>();
    for(BufferedReader br : readers) {
      String line = "";
      wh<caret>ile (null != (line = br.readLine())) {
        result.add(line.trim());
      }
    }
    return result;
  }
}