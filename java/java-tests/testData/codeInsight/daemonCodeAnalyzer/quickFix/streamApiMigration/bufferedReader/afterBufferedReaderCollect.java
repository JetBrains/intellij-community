// "Replace with collect" "true"

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  List<String> test(BufferedReader br) throws IOException {
    List<String> result;
      result = br.lines().map(String::trim).collect(Collectors.toList());
    return result;
  }
}