// "Collapse loop with stream 'collect()'" "true-preview"

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  List<String> test(BufferedReader br) throws IOException {
    List<String> result = br.lines().map(String::trim).collect(Collectors.toList());
      return result;
  }
}