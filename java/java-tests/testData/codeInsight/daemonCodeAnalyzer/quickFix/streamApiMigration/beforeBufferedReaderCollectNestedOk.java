// "Replace with collect" "true"

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
  List<String> test(List<BufferedReader> readers) throws IOException {
    for(BufferedReader br : readers) {
      List<String> result = new ArrayList<>();
      String line = "";
      wh<caret>ile (null != (line = br.readLine())) {
        result.add(line.trim());
      }
      if(result.size() > 10) {
        return result;
      }
    }
  }
}