// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public void test(List<List<String>> list) {
      for (List<String> lst : list) {
          if (lst != null) {
              for (String s : lst) {
                  System.out.println(s);
              }
          }
      }
  }
}