/*
Value is always false (s.equals("."); line#16)
  According to hard-coded contract, method 'equals' returns 'false' value when this != parameter (equals; line#16)
    One of the following happens:
      Values cannot be equal because s.length != ".".length
        Left operand is in {2..Integer.MAX_VALUE} (s; line#16)
          Range is known from line #16 (s.startsWith("--"); line#16)
        and right operand is 1 ("."; line#16)
      or it's known that 's != "."' from line #16 (s.startsWith("--"); line#16)
 */

import java.util.Objects;

class Test {
  void test(String s) {
    if (s.startsWith("--") && <selection>s.equals(".")</selection>) {
      
    }
  }
}