/*
Value is always false (s.equals("."); line#20)
  According to hard-coded contract, method 'equals' returns 'false' when s != "." (equals; line#20)
    One of the following happens:
      Condition 's != "."' was deduced
        Values cannot be equal because "--".length != ".".length
          Left operand is 2 (s.startsWith("--"); line#20)
          and right operand is 1 (s.startsWith("--"); line#20)
        and it's known that 's == "--"' from line #20 (s.startsWith("--"); line#20)
      or values cannot be equal because s.length != ".".length
        Left operand is in {2..Integer.MAX_VALUE} (s; line#20)
          Range is known from line #20 (s.startsWith("--"); line#20)
        and right operand is 1 ("."; line#20)
 */

import java.util.Objects;

class Test {
  void test(String s) {
    if (s.startsWith("--") && <selection>s.equals(".")</selection>) {

    }
  }
}