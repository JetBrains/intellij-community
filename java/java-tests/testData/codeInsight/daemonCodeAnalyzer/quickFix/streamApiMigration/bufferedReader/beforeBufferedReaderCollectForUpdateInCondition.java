// "Replace with collect" "true"

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
  List<String> test(BufferedReader br) throws IOException {
    List<String> result = new ArrayList<>();
    for <caret>(String line; (line = br.readLine()) != null; ) {
      result.add(line.trim());
    }
    return result;
  }
}