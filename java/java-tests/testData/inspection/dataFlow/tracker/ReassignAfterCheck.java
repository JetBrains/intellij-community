/*
Value is always false (t == null; line#16)
  't' was assigned (=; line#15)
    The 'instanceof' check implies non-nullity (t.getNext() instanceof CharSequence; line#14)
 */

import org.jetbrains.annotations.Contract;

class Test {
  @Contract(pure=true)
  native Test getNext();
  
  void find(Test t) {
    while (t.getNext() instanceof CharSequence) {
      t = t.getNext();
      if (<selection>t == null</selection>) return;
    }
    System.out.println(t);
  }
}