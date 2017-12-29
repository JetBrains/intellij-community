// "Replace with Comparator chain" "false"

import java.util.*;

public class Main {
  Comparator<String> cmp = (a, b) -> <caret>{
    int res = a.substring(1).compareTo()
  }
}
