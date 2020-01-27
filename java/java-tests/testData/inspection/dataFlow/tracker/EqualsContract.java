/*
Value is always false (s.equals("."); line#14)
  According to hard-coded contract, method 'equals' returns 'false' value when this != parameter (equals; line#14)
    One of the following happens:
      It's known that 's != "."' from line #14 (s.startsWith("--"); line#14)
      or values cannot be equal because s.length != ".".length
        Left operand is in {2..Integer.MAX_VALUE} (s; line#14)
          Range is known from line #14 (s.startsWith("--"); line#14)
        and right operand is 1 ("."; line#14)
 */

import java.util.Objects;

class Test {
  void test(String s) {
    if (s.startsWith("--") && <selection>s.equals(".")</selection>) {
      
    }
  }
}