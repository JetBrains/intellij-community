// "Collapse loop with stream 'collect()'" "true-preview"

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  List<String> test(List<BufferedReader> readers) throws IOException {
    for(BufferedReader br : readers) {
      List<String> result;
        result = br.lines().map(String::trim).collect(Collectors.toList());
      if(result.size() > 10) {
        return result;
      }
    }
  }
}