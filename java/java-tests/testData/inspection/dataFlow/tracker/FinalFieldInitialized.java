/*
Value is always true (s == null; line#10)
  Field 's' is initialized to null (null; line#7)
 */

public class T {
  private final String s = null;
  
  void check() {
    if(<selection>s == null</selection>) {
      System.out.println();
    }
  }
}
