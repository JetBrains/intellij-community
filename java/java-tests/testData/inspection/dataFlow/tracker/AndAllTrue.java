/*
Value is always true (obj != null && obj2 != null; line#16)
  Operand #1 of &&-chain is true (obj != null; line#16)
    'obj' is known to be 'non-null' from line #13 (Objects.requireNonNull(obj); line#13)
  and operand #2 of &&-chain is true (obj2 != null; line#16)
    'obj2' is known to be 'non-null' from line #14 (Objects.requireNonNull(obj2); line#14)
 */

import java.util.Objects;

class T {
  void foo(Object obj, Object obj2) {
    Objects.requireNonNull(obj);
    Objects.requireNonNull(obj2);

    if (<selection>obj != null && obj2 != null</selection>) {
      System.out.println("Okay");
    }
  }
}