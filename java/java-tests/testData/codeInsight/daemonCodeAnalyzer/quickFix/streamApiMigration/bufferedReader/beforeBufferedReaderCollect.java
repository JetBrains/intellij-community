// "Collapse loop with stream 'collect()'" "true-preview"

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
  List<String> test(BufferedReader br) throws IOException {
    List<String> result = new ArrayList<>();
    String line = /*comment*/"";
    wh<caret>ile(null != (line = (br.readLine()))) {
      result.add(line.trim());
    }
    return result;
  }
}