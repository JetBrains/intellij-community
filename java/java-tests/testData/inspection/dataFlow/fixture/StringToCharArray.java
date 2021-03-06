import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Contract;

public class StringToCharArray {
  void test(String s) {
    if (s.startsWith("--")) {
      char[] arr = s.toCharArray();
      if (<warning descr="Condition 'arr.length > 1' is always 'true'">arr.length > 1</warning>) {}
      if (<warning descr="Condition 'arr.length == s.length()' is always 'true'">arr.length == s.length()</warning>) {}
    }
  }
}
