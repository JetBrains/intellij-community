/*
Value is always true (obj != null; line#13)
  'obj' was assigned (=; line#10)
    Expression cannot be null as it's newly created object (new Object(); line#10)
 */

import java.util.Objects;

public class T {
  Object obj = new Object();
  
  public T() {
    if (<selection>obj != null</selection>) {
      
    }
  }
}
