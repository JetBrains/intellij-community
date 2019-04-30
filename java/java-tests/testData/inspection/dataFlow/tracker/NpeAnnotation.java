/*
May be null (s)
  's' was assigned (loadString())
    Method 'loadString' is annotated as 'nullable' (@Nullable)
 */

import org.jetbrains.annotations.Nullable;

class Test {
  void test() {
    String s = loadString();
    System.out.println(<selection>s</selection>.trim());
  }
  
  native @Nullable String loadString(); 
}