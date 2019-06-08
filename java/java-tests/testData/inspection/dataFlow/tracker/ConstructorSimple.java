/*
Value is always true (right != null; line#11)
  'right' is known to be 'non-null' from line #10 (Objects.requireNonNull(right); line#10)
 */

import java.util.Objects;

public class T {
  public T(Object right, boolean mode) {
    Objects.requireNonNull(right);
    if (mode && <selection>right != null</selection>) {
      System.out.println("okay");
    }
  }
}
