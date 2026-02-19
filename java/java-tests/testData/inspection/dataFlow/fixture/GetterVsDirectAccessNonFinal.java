import java.util.*;
import org.jetbrains.annotations.*;

class Test {
  private int x, y;

  int getX() {
    return x;
  }

  int getY() {
    return y;
  }
  
  static void test(Test t) {
    if (t.getX() == t.getY()) {
      if (t.x == t.y) { // Who knows, probably subclass
        
      }
    }
    if (t.getClass() == Test.class) {
      if (t.getX() == t.getY()) {
        if (<warning descr="Condition 't.x == t.y' is always 'true'">t.x == t.y</warning>) { // Definitely not subclass

        }
      }
    }
  }

  static void test2() {
    Test t = new Test();
    if (t.getX() == t.getY()) {
      if (<warning descr="Condition 't.x == t.y' is always 'true'">t.x == t.y</warning>) { // Definitely not subclass

      }
    }
  }
}