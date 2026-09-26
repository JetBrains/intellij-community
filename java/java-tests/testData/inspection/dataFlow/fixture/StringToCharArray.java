import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Contract;

public class StringToCharArray {
  final String field;
  final int[] bogus = new int[128];

  StringToCharArray(String s) {
    if (s.isEmpty()) throw new IllegalArgumentException();
    field = s;
    char[] chars = field.toCharArray();
    if (<warning descr="Condition 'chars.length != field.length()' is always 'false'">chars.length != field.length()</warning>) {}
    if (<warning descr="Condition 's.isEmpty()' is always 'false'">s.isEmpty()</warning>) {

    }
  }

  void test(String s) {
    if (s.startsWith("--")) {
      char[] arr = s.toCharArray();
      if (<warning descr="Condition 'arr.length > 1' is always 'true'">arr.length > 1</warning>) {}
      if (<warning descr="Condition 'arr.length == s.length()' is always 'true'">arr.length == s.length()</warning>) {}
    }
  }
}
