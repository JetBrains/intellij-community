import java.util.*;

class Test {
  void useTest() {
    String color = <caret>makeColor(Math.random() > 0.5);
    System.out.println("Color is " + color);
  }

  private String makeColor(boolean b) {
    if (b) {
      return "Foo";
    } else {
      return "Fie";
    }
  }
}