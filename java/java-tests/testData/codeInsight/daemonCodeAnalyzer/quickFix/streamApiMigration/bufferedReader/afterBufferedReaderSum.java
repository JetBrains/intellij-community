// "Replace with sum()" "true"

import java.io.BufferedReader;
import java.io.IOException;

public class Main {
  void test(BufferedReader br) throws IOException {
      long count = br.lines().map(String::trim).mapToLong(String::length).sum();
      System.out.println(count);
  }
}