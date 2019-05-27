/*
Value is always false (obj == null; line#15)
  Field 'obj' is initialized to 'non-null' value (new Object(); line#10)
 */

public class T {
  private final Object obj;
  
  T() {
    obj = new Object();
  }
  
  
  void check() {
    if(<selection>obj == null</selection>) {
      System.out.println();
    }
  }
}
