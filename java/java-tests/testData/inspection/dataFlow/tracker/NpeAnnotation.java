/*
May be null (s; line#12)
  's' was assigned (=; line#11)
    Method 'loadString' is annotated as 'nullable' (@Nullable; line#15)
 */

import org.jetbrains.annotations.Nullable;

class Test {
  void test() {
    String s = loadString();
    System.out.println(<selection>s</selection>.trim());
  }
  
  native @Nullable String loadString(); 
}