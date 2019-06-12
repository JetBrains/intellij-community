/*
Value is always false (obj.equals(null); line#8)
  According to hard-coded contract, method 'equals' returns 'false' value when parameter == null (equals; line#8)
 */

public class T {
  public void foo(Object obj) {
    if (<selection>obj.equals(null)</selection>) {
      System.out.println();
    }
  }
}
